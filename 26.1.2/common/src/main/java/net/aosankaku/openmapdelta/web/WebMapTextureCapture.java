// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBuffer.MappedView;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.aosankaku.openmapdelta.marker.MarkerIconCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4fStack;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Copies client-owned skin and marker images into immutable snapshots for HTTP and renderer readers. */
public final class WebMapTextureCapture {
    public record Published(String key, WebMapSnapshot.Image image) {}
    private record CaptureResources(AutoCloseable[] resources) {
        void close() {
            for (AutoCloseable resource : resources) {
                try { resource.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static final WebMapIconCache CACHE = new WebMapIconCache();
    private static final LinkedHashMap<String, Published> LATEST = new LinkedHashMap<>(32, .75f, true);
    private static final Set<String> PENDING_ITEMS = new HashSet<>();
    private static final Map<String, Long> ITEM_RETRY_AFTER = new HashMap<>();
    private static final Set<String> PENDING_EMOJI = new HashSet<>();
    private static final Map<String, Long> EMOJI_RETRY_AFTER = new HashMap<>();
    private static final int ITEM_ICON_SIZE = 32;
    private static final int MAX_PENDING_ITEMS = 2;
    private static final int MAX_PENDING_EMOJI = 3;
    private static final int MAX_EMOJI_TEXTURE_DIMENSION = 1024;
    private static final long MAX_EMOJI_READBACK_BYTES = 4L * 1024L * 1024L;
    private static final long CAPTURE_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static ProjectionMatrixBuffer itemProjectionBuffer;
    private static long generation = Long.MIN_VALUE;
    private static long captureWindowStart = Long.MIN_VALUE;
    private static int itemCapturesInWindow;
    private static long clearEpoch;
    private static boolean releaseProjectionAfterPending;

    private WebMapTextureCapture() {}

    public static void beginPublish(Minecraft mc) {
        syncGeneration();
        refreshCaptureWindow();
    }

    public static Published playerFace(Minecraft mc, UUID playerId, Identifier location) {
        syncGeneration();
        refreshCaptureWindow();
        if (location == null || !RenderSystem.isOnRenderThread()) return latest("player:" + playerId);
        return remember("player:" + playerId, captureTexture(mc, "player:" + playerId, location,
                0, 0, 8, 8, 64, 64, true));
    }

    public static Published markerIcon(Minecraft mc, MarkerIconCatalog.Entry entry) {
        syncGeneration();
        refreshCaptureWindow();
        if (entry == null) return null;
        if (entry.id().startsWith("emoji:emojiful:")) {
            MarkerIconCatalog.Texture frame = MarkerIconCatalog.resolveTexture(entry);
            if (frame == null) {
                LATEST.remove("marker:" + entry.id());
                return null;
            }
            requestEmojiCapture(mc, entry.id(), frame);
            return remember("marker:" + entry.id(), cachedEmojiPublished(entry.id(), frame));
        }
        if (!entry.item().isEmpty()) {
            String identity = itemIdentity(entry.id(), generation);
            requestItemCapture(mc, entry, identity);
            return remember("marker:" + entry.id(), cachedPublished(identity));
        }
        MarkerIconCatalog.Texture texture = entry.texture();
        if (texture == null) return latest("marker:" + entry.id());
        return remember("marker:" + entry.id(), captureTexture(mc, "marker:" + entry.id(), texture.location(),
                texture.u(), texture.v(), texture.width(), texture.height(),
                texture.textureWidth(), texture.textureHeight(), false));
    }

    /** Returns the most recently completed raster, scheduling a bounded render-thread capture on a cache miss. */
    public static WebMapImagePixels markerItemPixels(Minecraft mc, MarkerIconCatalog.Entry entry) {
        syncGeneration();
        refreshCaptureWindow();
        if (entry == null) return null;
        if (entry.id().startsWith("emoji:emojiful:")) {
            MarkerIconCatalog.Texture frame = MarkerIconCatalog.resolveTexture(entry);
            if (frame == null) {
                LATEST.remove("marker:" + entry.id());
                return null;
            }
            requestEmojiCapture(mc, entry.id(), frame);
            Published published = cachedEmojiPublished(entry.id(), frame);
            if (published == null) published = latest("marker:" + entry.id());
            if (published == null) return null;
            WebMapSnapshot.Image image = published.image();
            return new WebMapImagePixels(image.width(), image.height(), image.copyPixels());
        }
        if (entry.item().isEmpty()) return null;
        String identity = itemIdentity(entry.id(), generation);
        requestItemCapture(mc, entry, identity);
        WebMapSnapshot.Image image = CACHE.peek(identity, generation);
        return image == null ? null : new WebMapImagePixels(image.width(), image.height(), image.copyPixels());
    }

    public static void clear() {
        CACHE.clear();
        LATEST.clear();
        ITEM_RETRY_AFTER.clear();
        EMOJI_RETRY_AFTER.clear();
        generation = Long.MIN_VALUE;
        captureWindowStart = Long.MIN_VALUE;
        itemCapturesInWindow = 0;
        clearEpoch++;
        releaseProjectionAfterPending = true;
        releaseProjectionBufferIfIdle();
    }

    private static void requestItemCapture(Minecraft mc, MarkerIconCatalog.Entry entry, String identity) {
        if (!RenderSystem.isOnRenderThread() || CACHE.peek(identity, generation) != null) return;
        long now = System.nanoTime();
        if (now < ITEM_RETRY_AFTER.getOrDefault(identity, 0L) || PENDING_ITEMS.contains(identity)
                || PENDING_ITEMS.size() >= MAX_PENDING_ITEMS || itemCapturesInWindow >= MAX_PENDING_ITEMS
                || !CACHE.consumeCapturePermit(generation)) return;
        itemCapturesInWindow++;
        PENDING_ITEMS.add(identity);
        captureItem(mc, entry.item().copy(), entry.id(), identity, generation, clearEpoch);
    }

    /** Schedules bounded readback for an already-uploaded Emojiful frame. */
    private static void requestEmojiCapture(Minecraft mc, String markerId, MarkerIconCatalog.Texture frame) {
        if (frame == null || !RenderSystem.isOnRenderThread()) return;
        AbstractTexture texture;
        try { texture = mc.getTextureManager().getTexture(frame.location()); }
        catch (RuntimeException unavailable) { return; }
        if (texture == null || texture.getTexture() == null || texture.getTexture().isClosed()) return;
        String identity = emojiIdentity(markerId, frame, texture, generation);
        if (CACHE.peek(identity, generation) != null || PENDING_EMOJI.contains(identity)) return;
        long now = System.nanoTime();
        if (now < EMOJI_RETRY_AFTER.getOrDefault(identity, 0L)
                || PENDING_EMOJI.size() >= MAX_PENDING_EMOJI
                || !CACHE.consumeCapturePermit(generation)) return;
        PENDING_EMOJI.add(identity);
        captureEmojiFrame(texture, frame, markerId, identity, generation, clearEpoch);
    }

    private static void captureEmojiFrame(AbstractTexture texture, MarkerIconCatalog.Texture frame,
                                          String markerId, String identity,
                                          long captureGeneration, long captureEpoch) {
        RenderSystem.assertOnRenderThread();
        GpuBuffer readback = null;
        TextureTarget captureTarget = null;
        boolean readbackQueued = false;
        try {
            var gpuTexture = texture.getTexture();
            if (gpuTexture == null || gpuTexture.isClosed()) throw new IllegalStateException("Emojiful texture is unavailable");
            int width = gpuTexture.getWidth(0);
            int height = gpuTexture.getHeight(0);
            long byteCount = (long) width * height * 4L;
            if (width <= 0 || height <= 0 || width > MAX_EMOJI_TEXTURE_DIMENSION
                    || height > MAX_EMOJI_TEXTURE_DIMENSION || byteCount > MAX_EMOJI_READBACK_BYTES) {
                throw new IllegalArgumentException("Emojiful texture exceeds readback bounds");
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            var readbackTexture = gpuTexture;
            if (gpuTexture.getFormat() != TextureFormat.RGBA8
                    || (gpuTexture.usage() & GpuTexture.USAGE_COPY_SRC) == 0) {
                captureTarget = new TextureTarget("OpenMapMC Delta Emojiful texture copy", width, height, false);
                try (RenderPass pass = encoder.createRenderPass(
                        () -> "OpenMapMC Delta Emojiful texture copy", captureTarget.getColorTextureView(),
                        java.util.OptionalInt.empty())) {
                    pass.setPipeline(RenderPipelines.TRACY_BLIT);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.bindTexture("InSampler", texture.getTextureView(), texture.getSampler());
                    pass.disableScissor();
                    pass.draw(0, 3);
                }
                readbackTexture = captureTarget.getColorTexture();
            }
            readback = RenderSystem.getDevice().createBuffer(
                    () -> "OpenMapMC Delta Emojiful frame readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, byteCount);
            GpuBuffer retainedReadback = readback;
            TextureTarget retainedTarget = captureTarget;
            int retainedWidth = width, retainedHeight = height;
            encoder.copyTextureToBuffer(readbackTexture, readback, 0L,
                    () -> completeEmojiCapture(markerId, identity, captureGeneration, captureEpoch,
                            frame, retainedWidth, retainedHeight, retainedReadback, retainedTarget),
                    0, 0, 0, width, height);
            readbackQueued = true;
        } catch (Throwable failure) {
            recordEmojiRetry(identity);
        } finally {
            if (!readbackQueued) {
                PENDING_EMOJI.remove(identity);
                if (readback != null) {
                    try { readback.close(); } catch (Throwable ignored) {}
                }
                if (captureTarget != null) {
                    try { captureTarget.destroyBuffers(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    private static void completeEmojiCapture(String markerId, String identity,
                                             long captureGeneration, long captureEpoch,
                                             MarkerIconCatalog.Texture frame,
                                             int width, int height, GpuBuffer readback,
                                             TextureTarget captureTarget) {
        try (MappedView mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(readback, true, false)) {
            ByteBuffer bytes = mapped.data();
            int required = Math.multiplyExact(Math.multiplyExact(width, height), 4);
            if (bytes.remaining() < required) throw new IllegalStateException("Short emoji texture readback");
            int[] abgr = new int[width * height];
            // NativeImage upload row zero is the source's row zero. Keep straight alpha unchanged.
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int source = (y * width + x) * 4;
                    int red = bytes.get(source) & 255;
                    int green = bytes.get(source + 1) & 255;
                    int blue = bytes.get(source + 2) & 255;
                    int alpha = bytes.get(source + 3) & 255;
                    abgr[y * width + x] = (alpha << 24) | (blue << 16) | (green << 8) | red;
                }
            }
            if (captureEpoch == clearEpoch && captureGeneration == generation
                    && captureGeneration == MarkerIconCatalog.resourceGeneration()) {
                WebMapImagePixels pixels = WebMapImagePixels.fromNativeAbgr(width, height, abgr)
                        .textureRegion(frame.u(), frame.v(), frame.width(), frame.height(),
                                frame.textureWidth(), frame.textureHeight());
                WebMapSnapshot.Image image = pixels == null ? null
                        : CACHE.put(identity, captureGeneration, pixels);
                if (image != null) remember("marker:" + markerId, published(identity, image));
                EMOJI_RETRY_AFTER.remove(identity);
            }
        } catch (Throwable failure) {
            if (captureEpoch == clearEpoch) recordEmojiRetry(identity);
        } finally {
            PENDING_EMOJI.remove(identity);
            try { readback.close(); } catch (Throwable ignored) {}
            if (captureTarget != null) {
                try { captureTarget.destroyBuffers(); } catch (Throwable ignored) {}
            }
        }
    }

    private static String emojiIdentity(String markerId, MarkerIconCatalog.Texture frame,
                                        AbstractTexture texture, long resourceGeneration) {
        var gpuTexture = texture.getTexture();
        return "emoji-frame:" + markerId + ":" + frame.location() + ":"
                + System.identityHashCode(gpuTexture) + ":" + frame.u() + ":" + frame.v()
                + ":" + frame.width() + ":" + frame.height() + ":g" + resourceGeneration;
    }

    private static Published cachedEmojiPublished(String markerId, MarkerIconCatalog.Texture frame) {
        if (frame == null) return null;
        AbstractTexture texture;
        try { texture = Minecraft.getInstance().getTextureManager().getTexture(frame.location()); }
        catch (RuntimeException unavailable) { return latest("marker:" + markerId); }
        WebMapSnapshot.Image image = CACHE.peek(emojiIdentity(markerId, frame, texture, generation), generation);
        return image == null ? latest("marker:" + markerId) : published(
                emojiIdentity(markerId, frame, texture, generation), image);
    }

    private static void recordEmojiRetry(String identity) {
        EMOJI_RETRY_AFTER.put(identity, System.nanoTime() + RETRY_NANOS);
        while (EMOJI_RETRY_AFTER.size() > 256) {
            EMOJI_RETRY_AFTER.remove(EMOJI_RETRY_AFTER.keySet().iterator().next());
        }
    }

    private static void captureItem(Minecraft mc, ItemStack item, String markerId, String identity,
                                    long captureGeneration, long captureEpoch) {
        RenderSystem.assertOnRenderThread();
        TextureTarget target = null;
        GpuBuffer readback = null;
        boolean readbackQueued = false;
        boolean modelViewPushed = false;
        ByteBufferBuilder vertexBuilder = null;
        ByteBufferBuilder crumblingBuilder = null;
        FeatureRenderDispatcher dispatcher = null;
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        var oldProjectionBuffer = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType oldProjectionType = RenderSystem.getProjectionType();
        var oldShaderLights = RenderSystem.getShaderLights();
        var oldColorOverride = RenderSystem.outputColorTextureOverride;
        var oldDepthOverride = RenderSystem.outputDepthTextureOverride;
        ScissorState oldScissor = copyScissor(RenderSystem.getScissorStateForRenderTypeDraws());
        try {
            RenderSystem.disableScissorForRenderTypeDraws();
            modelView.pushMatrix();
            modelViewPushed = true;
            modelView.identity();

            target = new TextureTarget("OpenMapMC Delta marker item raster", ITEM_ICON_SIZE, ITEM_ICON_SIZE, true);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
            RenderSystem.outputColorTextureOverride = target.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();

            Projection projection = new Projection();
            projection.setupOrtho(-1000.0F, 1000.0F, ITEM_ICON_SIZE, ITEM_ICON_SIZE, true);
            if (itemProjectionBuffer == null) {
                itemProjectionBuffer = new ProjectionMatrixBuffer("OpenMapMC Delta marker item projection");
            }
            RenderSystem.setProjectionMatrix(itemProjectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);

            ItemStackRenderState state = new ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state, item, ItemDisplayContext.GUI, mc.level, mc.player, 0);
            mc.gameRenderer.getLighting().setupFor(state.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);

            vertexBuilder = new ByteBufferBuilder(4096);
            crumblingBuilder = new ByteBufferBuilder(4096);
            MultiBufferSource.BufferSource vertexBuffers = MultiBufferSource.immediate(vertexBuilder);
            MultiBufferSource.BufferSource crumblingBuffers = MultiBufferSource.immediate(crumblingBuilder);
            SubmitNodeStorage submits = new SubmitNodeStorage();
            dispatcher = new FeatureRenderDispatcher(submits, mc.getModelManager(), vertexBuffers,
                    mc.getAtlasManager(), new OutlineBufferSource(), crumblingBuffers, mc.font,
                    mc.gameRenderer.getGameRenderState());

            PoseStack pose = new PoseStack();
            pose.translate(ITEM_ICON_SIZE / 2.0F, ITEM_ICON_SIZE / 2.0F, 0.0F);
            pose.scale(ITEM_ICON_SIZE, -ITEM_ICON_SIZE, ITEM_ICON_SIZE);
            state.submit(pose, submits, 15728880, OverlayTexture.NO_OVERLAY, 0);
            dispatcher.renderAllFeatures();
            vertexBuffers.endBatch();
            crumblingBuffers.endBatch();

            readback = RenderSystem.getDevice().createBuffer(
                    () -> "OpenMapMC Delta marker item readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                    ITEM_ICON_SIZE * ITEM_ICON_SIZE * 4L);
            TextureTarget retainedTarget = target;
            GpuBuffer retainedReadback = readback;
            CaptureResources retainedResources = new CaptureResources(
                    new AutoCloseable[]{dispatcher, vertexBuilder, crumblingBuilder});
            encoder.copyTextureToBuffer(target.getColorTexture(), readback, 0L,
                    () -> completeItemCapture(markerId, identity, captureGeneration, captureEpoch,
                            retainedTarget, retainedReadback, retainedResources), 0);
            readbackQueued = true;
        } catch (Throwable failure) {
            recordRetry(identity);
        } finally {
            RenderSystem.outputColorTextureOverride = oldColorOverride;
            RenderSystem.outputDepthTextureOverride = oldDepthOverride;
            RenderSystem.setProjectionMatrix(oldProjectionBuffer, oldProjectionType);
            RenderSystem.setShaderLights(oldShaderLights);
            restoreScissor(oldScissor);
            if (modelViewPushed) modelView.popMatrix();
            if (!readbackQueued) {
                new CaptureResources(new AutoCloseable[]{dispatcher, vertexBuilder, crumblingBuilder}).close();
                PENDING_ITEMS.remove(identity);
                releaseProjectionBufferIfIdle();
                if (target != null) {
                    try { target.destroyBuffers(); } catch (Throwable ignored) {}
                }
                if (readback != null) {
                    try { readback.close(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    private static void completeItemCapture(String markerId, String identity,
                                            long captureGeneration, long captureEpoch,
                                            TextureTarget target, GpuBuffer readback,
                                            CaptureResources resources) {
        try (MappedView mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(readback, true, false)) {
            ByteBuffer bytes = mapped.data();
            int required = ITEM_ICON_SIZE * ITEM_ICON_SIZE * 4;
            if (bytes.remaining() < required) throw new IllegalStateException("Short GPU item readback");
            int[] abgr = new int[ITEM_ICON_SIZE * ITEM_ICON_SIZE];
            for (int y = 0; y < ITEM_ICON_SIZE; y++) {
                int sourceY = ITEM_ICON_SIZE - 1 - y;
                for (int x = 0; x < ITEM_ICON_SIZE; x++) {
                    int source = (sourceY * ITEM_ICON_SIZE + x) * 4;
                    int red = bytes.get(source) & 255;
                    int green = bytes.get(source + 1) & 255;
                    int blue = bytes.get(source + 2) & 255;
                    int alpha = bytes.get(source + 3) & 255;
                    if (alpha > 0 && alpha < 255) {
                        red = Math.min(255, (red * 255 + alpha / 2) / alpha);
                        green = Math.min(255, (green * 255 + alpha / 2) / alpha);
                        blue = Math.min(255, (blue * 255 + alpha / 2) / alpha);
                    }
                    abgr[y * ITEM_ICON_SIZE + x] = (alpha << 24) | (blue << 16) | (green << 8) | red;
                }
            }
            if (captureEpoch == clearEpoch && captureGeneration == generation
                    && captureGeneration == MarkerIconCatalog.resourceGeneration()) {
                WebMapSnapshot.Image image = CACHE.put(identity, captureGeneration,
                        new WebMapImagePixels(ITEM_ICON_SIZE, ITEM_ICON_SIZE, abgr));
                if (image != null) {
                    remember("marker:" + markerId, published(identity, image));
                }
                ITEM_RETRY_AFTER.remove(identity);
            }
        } catch (Throwable failure) {
            if (captureEpoch == clearEpoch) recordRetry(identity);
        } finally {
            PENDING_ITEMS.remove(identity);
            releaseProjectionBufferIfIdle();
            try { readback.close(); } catch (Throwable ignored) {}
            try { target.destroyBuffers(); } catch (Throwable ignored) {}
            resources.close();
        }
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

    private static String itemIdentity(String id, long resourceGeneration) {
        return id + ":g" + resourceGeneration;
    }

    private static void recordRetry(String identity) {
        ITEM_RETRY_AFTER.put(identity, System.nanoTime() + RETRY_NANOS);
        while (ITEM_RETRY_AFTER.size() > 256) {
            ITEM_RETRY_AFTER.remove(ITEM_RETRY_AFTER.keySet().iterator().next());
        }
    }

    private static void releaseProjectionBufferIfIdle() {
        if (!releaseProjectionAfterPending || !PENDING_ITEMS.isEmpty()) return;
        releaseProjectionAfterPending = false;
        if (itemProjectionBuffer != null) {
            try { itemProjectionBuffer.close(); } catch (Throwable ignored) {}
            itemProjectionBuffer = null;
        }
    }

    private static Published cachedPublished(String identity) {
        WebMapSnapshot.Image image = CACHE.peek(identity, generation);
        return image == null ? null : published(identity, image);
    }

    private static Published captureTexture(Minecraft mc, String identity, Identifier location,
                                            int u, int v, int width, int height,
                                            int textureWidth, int textureHeight, boolean face) {
        if (!RenderSystem.isOnRenderThread()) return null;
        Resource resource = mc.getResourceManager().getResource(location).orElse(null);
        if (resource != null) {
            String source = "resource:" + identity + ":" + location + ":" + u + ":" + v + ":" + width
                    + ":" + height + ":" + textureWidth + ":" + textureHeight + ":g" + generation;
            WebMapSnapshot.Image image = CACHE.get(source, generation, () -> {
                try (InputStream stream = resource.open()) {
                    BufferedImage decoded = ImageIO.read(stream);
                    if (decoded == null) return null;
                    WebMapImagePixels pixels = WebMapImagePixels.fromArgb(decoded);
                    return face ? pixels.minecraftFace()
                            : pixels.textureRegion(u, v, width, height, textureWidth, textureHeight);
                }
            });
            return published(source, image);
        }

        AbstractTexture texture;
        try { texture = mc.getTextureManager().getTexture(location); }
        catch (RuntimeException unavailable) { return null; }
        if (!(texture instanceof DynamicTexture dynamic) || dynamic.getPixels() == null) return null;
        String source = "dynamic:" + identity + ":" + location + ":" + System.identityHashCode(dynamic)
                + ":" + u + ":" + v + ":" + width + ":" + height + ":g" + generation;
        WebMapSnapshot.Image image = CACHE.get(source, generation, () -> {
            NativeImage nativeImage = dynamic.getPixels();
            if (nativeImage == null) return null;
            int sourceWidth = nativeImage.getWidth(), sourceHeight = nativeImage.getHeight();
            if (sourceWidth <= 0 || sourceHeight <= 0 || sourceWidth > 8192 || sourceHeight > 8192
                    || (long) sourceWidth * sourceHeight > 16_777_216L) return null;
            int[] abgr = nativeImage.getPixelsABGR();
            if (abgr == null || abgr.length != sourceWidth * sourceHeight) return null;
            WebMapImagePixels pixels = WebMapImagePixels.fromNativeAbgr(sourceWidth, sourceHeight, abgr);
            return face ? pixels.minecraftFace() : pixels.textureRegion(u, v, width, height, textureWidth, textureHeight);
        });
        return published(source, image);
    }

    private static Published published(String source, WebMapSnapshot.Image image) {
        return image == null ? null : new Published(WebMapIconCache.opaqueKey(source), image);
    }

    private static Published remember(String identity, Published current) {
        if (current != null) {
            LATEST.put(identity, current);
            while (LATEST.size() > 512) LATEST.remove(LATEST.keySet().iterator().next());
            return current;
        }
        return latest(identity);
    }

    private static Published latest(String identity) { return LATEST.get(identity); }

    private static void syncGeneration() {
        long current = MarkerIconCatalog.resourceGeneration();
        if (current == generation) return;
        generation = current;
        CACHE.activateGeneration(current);
        LATEST.clear();
        ITEM_RETRY_AFTER.clear();
        clearEpoch++;
        itemCapturesInWindow = 0;
        releaseProjectionAfterPending = true;
        releaseProjectionBufferIfIdle();
    }

    private static void refreshCaptureWindow() {
        long now = System.nanoTime();
        if (captureWindowStart != Long.MIN_VALUE && now - captureWindowStart < CAPTURE_WINDOW_NANOS) return;
        captureWindowStart = now;
        itemCapturesInWindow = 0;
        CACHE.beginPublish(16);
    }
}
