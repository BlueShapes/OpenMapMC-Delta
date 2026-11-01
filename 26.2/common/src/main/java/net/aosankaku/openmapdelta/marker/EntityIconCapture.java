// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.marker;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.aosankaku.openmapdelta.mixin.LivingEntityRendererModelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Matrix4fStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Captures a registered entity model once to a bounded, reusable client-side icon image. */
public final class EntityIconCapture {
    public static final int IMAGE_SIZE = 64;
    private static final int IMAGE_BYTES = IMAGE_SIZE * IMAGE_SIZE * 4;
    private static final float IMAGE_PADDING = 6.0F;
    private static final Logger LOGGER = LoggerFactory.getLogger("OpenMapDelta");

    private EntityIconCapture() {}

    /**
     * Generates one image on the client render thread. A safely extracted model head is attempted
     * first; an empty or failed head capture automatically falls back to the complete renderer.
     * The caller owns a successful NativeImage and must close or transfer it.
     */
    public static void capture(Minecraft mc, Entity entity, boolean preferHead, Consumer<NativeImage> completion) {
        if (mc == null || entity == null || completion == null) return;
        Completion once = new Completion(completion);
        if (!RenderSystem.isOnRenderThread()) {
            mc.execute(() -> captureOnRenderThread(mc, entity, preferHead, once));
            return;
        }
        captureOnRenderThread(mc, entity, preferHead, once);
    }

    private static void captureOnRenderThread(Minecraft mc, Entity entity, boolean preferHead, Completion completion) {
        RenderSystem.assertOnRenderThread();
        if (tryCapture(mc, entity, preferHead, completion)) return;
        if (preferHead && tryCapture(mc, entity, false, completion)) return;
        completion.deliver(null);
    }

    /** Returns true only when the GPU copy was queued and will complete asynchronously. */
    private static boolean tryCapture(Minecraft mc, Entity entity, boolean preferHead, Completion completion) {
        TextureTarget target = null;
        GpuBuffer readback = null;
        ProjectionMatrixBuffer projectionBuffer = null;
        RenderBuffers renderBuffers = null;
        FeatureRenderDispatcher featureDispatcher = null;
        boolean copyQueued = false;
        boolean modelViewPushed = false;
        HeadOnlyEntityModel<?> headOnlyModel = null;
        RendererModelSnapshot rendererModelSnapshot = null;
        EntityHeadCapture.PoseRestorations submittedModelPoses = new EntityHeadCapture.PoseRestorations();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        var previousLights = RenderSystem.getShaderLights();
        var previousColorTarget = RenderSystem.outputColorTextureOverride;
        var previousDepthTarget = RenderSystem.outputDepthTextureOverride;
        ScissorState previousScissor = copyScissor(RenderSystem.getScissorStateForRenderTypeDraws());
        try {
            rendererModelSnapshot = snapshotRendererModel(mc, entity);
            RenderSystem.disableScissorForRenderTypeDraws();
            modelView.pushMatrix();
            modelViewPushed = true;
            modelView.identity();

            target = new TextureTarget("OpenMapMC Delta entity marker icon", IMAGE_SIZE, IMAGE_SIZE,
                    true, GpuFormat.RGBA8_UNORM);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(target.getColorTexture(), new Vector4f(0.0F),
                    target.getDepthTexture(), 0.0);
            RenderSystem.outputColorTextureOverride = target.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();

            Projection projection = new Projection();
            projection.setupOrtho(-1000.0F, 1000.0F, IMAGE_SIZE, IMAGE_SIZE, true);
            projectionBuffer = new ProjectionMatrixBuffer("OpenMapMC Delta entity marker projection");
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);

            renderBuffers = new RenderBuffers(0);
            SubmitNodeStorage submits = new ModelTrackingSubmitNodeStorage(submittedModelPoses);
            featureDispatcher = new FeatureRenderDispatcher(renderBuffers, mc.getModelManager(), mc.getAtlasManager(),
                    mc.font, mc.gameRenderer.gameRenderState());

            boolean rendered;
            if (preferHead) {
                headOnlyModel = renderHead(mc, entity, submits);
                rendered = headOnlyModel != null;
            } else {
                rendered = renderEntity(mc, entity, submits);
            }
            if (!rendered) return false;
            featureDispatcher.renderAllFeatures(submits);
            renderBuffers.endFrame();
            if (headOnlyModel != null) headOnlyModel.restoreSourcePose();

            readback = RenderSystem.getDevice().createBuffer(
                    () -> "OpenMapMC Delta entity marker readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                    IMAGE_BYTES);
            TextureTarget retainedTarget = target;
            GpuBuffer retainedReadback = readback;
            ProjectionMatrixBuffer retainedProjection = projectionBuffer;
            CaptureResources retainedResources = new CaptureResources(featureDispatcher, renderBuffers);
            encoder.copyTextureToBuffer(target.getColorTexture(), readback, 0L,
                    () -> completeCapture(mc, entity, preferHead, completion, retainedTarget, retainedReadback,
                            retainedProjection, retainedResources), 0);
            copyQueued = true;
            return true;
        } catch (Throwable failure) {
            LOGGER.debug("Could not capture an entity map icon for {}", typeId(entity), failure);
            return false;
        } finally {
            if (headOnlyModel != null) {
                try { headOnlyModel.restoreSourcePose(); } catch (Throwable ignored) {}
            }
            try { submittedModelPoses.restore(); }
            catch (Throwable failure) { LOGGER.debug("Could not fully restore submitted entity model poses", failure); }
            if (rendererModelSnapshot != null) {
                try { rendererModelSnapshot.restore(); }
                catch (Throwable failure) { LOGGER.debug("Could not restore the entity renderer model", failure); }
            }
            RenderSystem.outputColorTextureOverride = previousColorTarget;
            RenderSystem.outputDepthTextureOverride = previousDepthTarget;
            RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            RenderSystem.setShaderLights(previousLights);
            restoreScissor(previousScissor);
            if (modelViewPushed) modelView.popMatrix();
            if (!copyQueued) {
                new CaptureResources(featureDispatcher, renderBuffers).close();
                if (target != null) {
                    try { target.destroyBuffers(); } catch (Throwable ignored) {}
                }
                if (readback != null) {
                    try { readback.close(); } catch (Throwable ignored) {}
                }
                if (projectionBuffer != null) {
                    try { projectionBuffer.close(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    private static boolean renderEntity(Minecraft mc, Entity entity, SubmitNodeStorage submits) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderer renderer = dispatcher.getRenderer(entity);
        EntityRenderState state = dispatcher.extractEntity(entity, 1.0F);
        if (state == null) return false;
        prepareRenderState(state);
        normalizeUiRotation(state);

        LivingEntityRendererModelAccessor accessor = renderer instanceof LivingEntityRenderer
                ? (LivingEntityRendererModelAccessor) renderer : null;
        EntityModel<?> originalModel = accessor == null ? null : accessor.odysseymap$getModel();
        EntityHeadCapture.PoseSnapshot originalPose = originalModel == null ? null
                : EntityHeadCapture.snapshot(originalModel);
        EntityIconFraming.Frame frame;
        ModelProbeStorage probe = new ModelProbeStorage(state);
        try {
            PoseStack probePose = createRendererPose(entity);
            dispatcher.submit(state, new CameraRenderState(), 0.0, 0.0, 0.0, probePose, probe);

            frame = frameForEntity(entity, probe.sample);
        } finally {
            try { probe.restoreModels(); }
            finally {
                if (accessor != null && originalModel != null) {
                    accessor.odysseymap$setModel(originalModel);
                    EntityHeadCapture.restore(originalModel, originalPose);
                }
            }
        }
        if (frame == null) return false;
        PoseStack iconPose = new PoseStack();
        applyPortraitBasis(iconPose, frame);
        if (isVanillaFish(entity)) {
            iconPose.mulPose(new org.joml.Quaternionf().rotationY((float) (Math.PI / 2.0)));
        }
        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        dispatcher.submit(state, new CameraRenderState(), 0.0, 0.0, 0.0, iconPose, submits);
        return true;
    }

    /**
     * Runs the registered living renderer once into a probe collector so variant-switched model,
     * render type, light, overlay, and tint all come from the exact submitted base model.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static HeadOnlyEntityModel<?> renderHead(Minecraft mc, Entity entity, SubmitNodeStorage submits) {
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderer renderer = dispatcher.getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)
                || !(dispatcher.extractEntity(entity, 1.0F) instanceof LivingEntityRenderState state)) return null;

        LivingEntityRendererModelAccessor accessor = (LivingEntityRendererModelAccessor) livingRenderer;
        EntityModel<?> originalModel = accessor.odysseymap$getModel();
        if (originalModel == null) return null;
        EntityHeadCapture.PoseSnapshot originalPose = EntityHeadCapture.snapshot(originalModel);
        ModelProbeStorage probe = new ModelProbeStorage(state);
        HeadOnlyEntityModel<?> result = null;
        try {
            prepareRenderState(state);
            normalizeUiRotation(state);
            CameraRenderState camera = new CameraRenderState();
            PoseStack probePose = createRendererPose(entity);
            ((LivingEntityRenderer) livingRenderer).submit(state, probePose, probe, camera);
            ModelSubmission sample = probe.sample;
            if (sample == null || sample.model != originalModel) return null;
            ModelPart head = EntityHeadCapture.selectHead(sample.model);
            if (head == null) return null;

            HeadMeasurement measurement = EntityHeadCapture.inspectAnimatedPose((Model) sample.model, sample.state,
                    originalPose, () -> {
                        PoseStack extentsPose = new PoseStack();
                        extentsPose.last().set(sample.pose);
                        if (head != sample.model.root()) sample.model.root().translateAndRotate(extentsPose);
                        Bounds measured = new Bounds();
                        head.getExtentsForGui(extentsPose, measured::include);
                        // The root is restored when this inspection returns. Keep its animated
                        // parent transform with the bounds so the head is rendered in that frame.
                        return new HeadMeasurement(measured, extentsPose.last().copy());
                    });
            Bounds bounds = measurement.bounds();
            if (!bounds.isUsable()) return null;

            EntityIconFraming.Frame frame = EntityIconFraming.fitProjectedBounds(
                    bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, IMAGE_SIZE, IMAGE_PADDING);
            if (frame == null) return null;
            PoseStack headPose = new PoseStack();
            applyPortraitBasis(headPose, frame);
            headPose.mulPose(measurement.parentPose().pose());

            result = new HeadOnlyEntityModel((EntityModel) originalModel, state, head, originalPose);
            ((SubmitNodeStorage) submits).submitModel((Model) result, state, headPose, sample.renderType,
                    sample.light, sample.overlay, sample.tint, sample.sprite, sample.outline, sample.crumbling);
            mc.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            return result;
        } catch (Throwable failure) {
            LOGGER.debug("Could not extract a submitted entity head for {}", typeId(entity), failure);
            return null;
        } finally {
            try { probe.restoreModels(); }
            finally {
                accessor.odysseymap$setModel(originalModel);
                EntityHeadCapture.restore(originalModel, originalPose);
            }
        }
    }

    private static void prepareRenderState(EntityRenderState state) {
        state.shadowPieces.clear();
        state.shadowRadius = 0.0F;
        state.leashStates = null;
        state.outlineColor = 0;
        state.nameTag = null;
        state.scoreText = null;
        state.nameTagAttachment = null;
        state.displayFireAnimation = false;
        state.lightCoords = 15728880;
    }

    private static RendererModelSnapshot snapshotRendererModel(Minecraft mc, Entity entity) {
        EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer)) return null;
        LivingEntityRendererModelAccessor accessor = (LivingEntityRendererModelAccessor) renderer;
        EntityModel<?> model = accessor.odysseymap$getModel();
        if (model == null) return null;
        return new RendererModelSnapshot(accessor, model, EntityHeadCapture.snapshot(model));
    }

    private static void normalizeUiRotation(EntityRenderState state) {
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0F;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
        }
    }

    private static PoseStack createRendererPose(Entity entity) {
        PoseStack pose = new PoseStack();
        if (isVanillaFish(entity)) pose.mulPose(new org.joml.Quaternionf().rotationY((float) (Math.PI / 2.0)));
        return pose;
    }

    private static void applyPortraitBasis(PoseStack pose, EntityIconFraming.Frame frame) {
        pose.translate(frame.translateX(), frame.translateY(), 0.0F);
        pose.scale(frame.scale(), frame.scale(), -frame.scale());
        pose.mulPose(new org.joml.Quaternionf().rotationZ((float) Math.PI));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static EntityIconFraming.Frame frameForEntity(Entity entity, ModelSubmission sample) {
        if (sample != null) {
            try {
                EntityHeadCapture.PoseSnapshot pose = EntityHeadCapture.snapshot(sample.model);
                Bounds bounds = EntityHeadCapture.inspectAnimatedPose((Model) sample.model, sample.state, pose,
                        () -> {
                            PoseStack extentsPose = new PoseStack();
                            extentsPose.last().set(sample.pose);
                            Bounds measured = new Bounds();
                            sample.model.root().getExtentsForGui(extentsPose, measured::include);
                            return measured;
                        });
                if (bounds.isUsable()) {
                    EntityIconFraming.Frame frame = EntityIconFraming.fitProjectedBounds(
                            bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, IMAGE_SIZE, IMAGE_PADDING);
                    if (frame != null) return frame;
                }
            } catch (Throwable failure) {
                LOGGER.debug("Could not measure an entity model for its map icon", failure);
            }
        }
        return EntityIconFraming.fit((float) entity.getBbWidth(), (float) entity.getBbHeight(),
                IMAGE_SIZE, IMAGE_PADDING);
    }

    private static boolean isVanillaFish(Entity entity) {
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null || !"minecraft".equals(key.getNamespace())) return false;
        return switch (key.getPath()) {
            case "cod", "salmon", "pufferfish", "tropical_fish" -> true;
            default -> false;
        };
    }

    private static Identifier typeId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    private static void completeCapture(Minecraft mc, Entity entity, boolean preferHead, Completion completion,
                                        TextureTarget target, GpuBuffer readback,
                                        ProjectionMatrixBuffer projectionBuffer, CaptureResources resources) {
        NativeImage image = null;
        try (MappedView mapped = readback.slice().map(true, false)) {
            ByteBuffer pixels = mapped.data();
            if (pixels.remaining() >= IMAGE_BYTES) image = EntityIconPixels.crop(pixels, IMAGE_SIZE, IMAGE_SIZE, 1);
        } catch (Throwable failure) {
            LOGGER.debug("Could not read back an entity map icon for {}", typeId(entity), failure);
        } finally {
            try { readback.close(); } catch (Throwable ignored) {}
            try { target.destroyBuffers(); } catch (Throwable ignored) {}
            try { projectionBuffer.close(); } catch (Throwable ignored) {}
            resources.close();
        }

        if (image == null && preferHead) {
            captureOnRenderThread(mc, entity, false, completion);
            return;
        }
        completion.deliver(image);
    }

    private static ScissorState copyScissor(ScissorState current) {
        ScissorState copy = new ScissorState();
        if (current.enabled()) copy.enable(current.x(), current.y(), current.width(), current.height());
        return copy;
    }

    private static void restoreScissor(ScissorState saved) {
        if (saved.enabled()) RenderSystem.enableScissorForRenderTypeDraws(saved.x(), saved.y(), saved.width(), saved.height());
        else RenderSystem.disableScissorForRenderTypeDraws();
    }

    private record CaptureResources(FeatureRenderDispatcher featureDispatcher, RenderBuffers renderBuffers) {
        void close() {
            if (featureDispatcher != null) try { featureDispatcher.close(); } catch (Throwable ignored) {}
            if (renderBuffers != null) try { renderBuffers.close(); } catch (Throwable ignored) {}
        }
    }

    private record RendererModelSnapshot(LivingEntityRendererModelAccessor accessor, EntityModel<?> model,
                                          EntityHeadCapture.PoseSnapshot pose) {
        void restore() {
            accessor.odysseymap$setModel(model);
            EntityHeadCapture.restore(model, pose);
        }
    }

    private record ModelSubmission(Model<?> model, EntityRenderState state, PoseStack.Pose pose,
                                   RenderType renderType, int light, int overlay, int tint,
                                   TextureAtlasSprite sprite, int outline, CrumblingOverlay crumbling) {}

    private record HeadMeasurement(Bounds bounds, PoseStack.Pose parentPose) {}

    private static final class ModelProbeStorage extends SubmitNodeStorage {
        private final EntityRenderState expectedState;
        private final EntityHeadCapture.PoseRestorations modelPoses = new EntityHeadCapture.PoseRestorations();
        private ModelSubmission sample;
        private ModelProbeStorage(EntityRenderState expectedState) { this.expectedState = expectedState; }
        private <S> void capture(Model<? super S> model, S state, PoseStack pose, RenderType renderType,
                                 int light, int overlay, int tint, TextureAtlasSprite sprite, int outline,
                                 CrumblingOverlay crumbling) {
            modelPoses.capture((Model<?>) model);
            if (sample == null && state == expectedState) {
                sample = new ModelSubmission(model, expectedState, pose.last().copy(), renderType,
                        light, overlay, tint, sprite, outline, crumbling);
            }
        }
        private void restoreModels() { modelPoses.restore(); }
        @Override public <S> void submitModel(Model<? super S> model, S state, PoseStack pose,
                RenderType renderType, int light, int overlay, int tint, TextureAtlasSprite sprite,
                int outline, CrumblingOverlay crumbling) {
            capture(model, state, pose, renderType, light, overlay, tint, sprite, outline, crumbling);
        }
        @Override public SubmitNodeCollection order(int order) {
            var groups = getSubmitsPerOrder();
            SubmitNodeCollection collection = groups.get(order);
            if (collection == null) {
                collection = new ModelProbeCollection(this);
                groups.put(order, collection);
            }
            return collection;
        }
    }

    private static final class ModelProbeCollection extends SubmitNodeCollection {
        private final ModelProbeStorage owner;
        private ModelProbeCollection(ModelProbeStorage owner) { this.owner = owner; }
        @Override public <S> void submitModel(Model<? super S> model, S state, PoseStack pose,
                RenderType renderType, int light, int overlay, int tint, TextureAtlasSprite sprite,
                int outline, CrumblingOverlay crumbling) {
            owner.capture(model, state, pose, renderType, light, overlay, tint, sprite, outline, crumbling);
        }
    }

    private static final class ModelTrackingSubmitNodeStorage extends SubmitNodeStorage {
        private final EntityHeadCapture.PoseRestorations modelPoses;
        private ModelTrackingSubmitNodeStorage(EntityHeadCapture.PoseRestorations modelPoses) {
            this.modelPoses = modelPoses;
        }
        private void capture(Model<?> model) { modelPoses.capture(model); }
        @Override public SubmitNodeCollection order(int order) {
            var groups = getSubmitsPerOrder();
            SubmitNodeCollection collection = groups.get(order);
            if (collection == null) {
                collection = new ModelTrackingSubmitNodeCollection(this);
                groups.put(order, collection);
            }
            return collection;
        }
    }

    private static final class ModelTrackingSubmitNodeCollection extends SubmitNodeCollection {
        private final ModelTrackingSubmitNodeStorage owner;
        private ModelTrackingSubmitNodeCollection(ModelTrackingSubmitNodeStorage owner) { this.owner = owner; }
        @Override public <S> void submitModel(Model<? super S> model, S state, PoseStack pose,
                RenderType renderType, int light, int overlay, int tint, TextureAtlasSprite sprite,
                int outline, CrumblingOverlay crumbling) {
            owner.capture((Model<?>) model);
            super.submitModel(model, state, pose, renderType, light, overlay, tint, sprite, outline, crumbling);
        }
    }

    private static final class HeadOnlyEntityModel<S extends EntityRenderState> extends EntityModel<S> {
        private final EntityModel<S> source;
        private final S state;
        private final EntityHeadCapture.PoseSnapshot originalPose;
        private HeadOnlyEntityModel(EntityModel<S> source, S state, ModelPart head,
                                    EntityHeadCapture.PoseSnapshot originalPose) {
            super(head);
            this.source = source; this.state = state; this.originalPose = originalPose;
        }
        @Override public void setupAnim(S ignored) { source.setupAnim(state); }
        private void restoreSourcePose() { originalPose.restore(); }
    }

    private static final class Bounds {
        private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        void include(Vector3fc point) {
            minX = Math.min(minX, point.x()); minY = Math.min(minY, point.y()); minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y()); maxZ = Math.max(maxZ, point.z());
        }
        boolean isUsable() {
            return Float.isFinite(minX) && Float.isFinite(maxX) && Float.isFinite(minY) && Float.isFinite(maxY)
                    && width() > 0.001F && height() > 0.001F;
        }
        float width() { return maxX - minX; }
        float height() { return maxY - minY; }
        float centerX() { return (minX + maxX) / 2.0F; }
        float centerY() { return (minY + maxY) / 2.0F; }
        float centerZ() { return (minZ + maxZ) / 2.0F; }
    }

    private static final class Completion {
        private final AtomicBoolean delivered = new AtomicBoolean();
        private final Consumer<NativeImage> callback;
        private Completion(Consumer<NativeImage> callback) { this.callback = callback; }
        void deliver(NativeImage image) {
            if (!delivered.compareAndSet(false, true)) {
                if (image != null) image.close();
                return;
            }
            try { callback.accept(image); }
            catch (Throwable failure) {
                if (image != null) image.close();
                LOGGER.debug("Entity marker image callback failed", failure);
            }
        }
    }
}
