// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.aosankaku.openmapdelta.marker.MarkerIconCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

/** Copies client-owned skins and marker icons before publishing an immutable HTTP snapshot. */
public final class WebMapTextureCapture {
    public record Published(String key, WebMapSnapshot.Image image) {}
    private static final WebMapIconCache CACHE = new WebMapIconCache();
    private static long generation = Long.MIN_VALUE;
    private static final LinkedHashMap<String, Published> latest = new LinkedHashMap<>(32, .75f, true);
    private static final int MAX_ITEM_CAPTURES_PER_SECOND = 2;
    private static final int MAX_CAPTURED_TEXTURE_UNITS = 12;
    private static final long CAPTURE_WINDOW_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
    private static final long HTTP_TEXTURE_REFRESH_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    private static long captureWindowStart = Long.MIN_VALUE;
    private static int itemCaptureBudget;

    private WebMapTextureCapture() {}

    public static void beginPublish(Minecraft mc) {
        syncGeneration();
        refreshCaptureWindow();
    }

    public static Published playerFace(Minecraft mc, UUID playerId, ResourceLocation location) {
        syncGeneration(); refreshCaptureWindow();
        if (location == null || !RenderSystem.isOnRenderThread()) return latest("player:" + playerId);
        Captured captured = texture(mc, "player:" + playerId, location, 0, 0, 8, 8, 64, 64, true);
        return remember("player:" + playerId, published(captured));
    }

    public static Published markerIcon(Minecraft mc, MarkerIconCatalog.Entry entry) {
        syncGeneration(); refreshCaptureWindow();
        if (entry == null) return null;
        if (entry.id().startsWith("emoji:emojiful:")) {
            MarkerIconCatalog.Texture frame = MarkerIconCatalog.resolveTexture(entry);
            if (frame == null) {
                latest.remove("marker:" + entry.id());
                return null;
            }
            return remember("marker:" + entry.id(), published(emojiFrame(mc, entry, frame)));
        }
        if (!entry.item().isEmpty()) return remember("marker:" + entry.id(), itemIcon(mc, entry.id(), entry.item()));
        MarkerIconCatalog.Texture texture = entry.texture();
        if (texture == null) return latest("marker:" + entry.id());
        Captured captured = texture(mc, "marker:" + entry.id(), texture.location(), texture.u(), texture.v(),
                texture.width(), texture.height(), texture.textureWidth(), texture.textureHeight(), false);
        return remember("marker:" + entry.id(), published(captured));
    }

    /** Returns an immutable copy of the actual rendered item icon for world waypoint sprites. */
    public static WebMapImagePixels markerItemPixels(Minecraft mc, MarkerIconCatalog.Entry entry) {
        syncGeneration(); refreshCaptureWindow();
        if (entry != null && entry.id().startsWith("emoji:emojiful:")) {
            MarkerIconCatalog.Texture frame = MarkerIconCatalog.resolveTexture(entry);
            if (frame == null) {
                latest.remove("marker:" + entry.id());
                return null;
            }
            Captured captured = emojiFrame(mc, entry, frame);
            if (captured == null) {
                Published previous = latest("marker:" + entry.id());
                if (previous == null) return null;
                WebMapSnapshot.Image image = previous.image();
                return new WebMapImagePixels(image.width(), image.height(), image.copyPixels());
            }
            WebMapSnapshot.Image image = captured.image();
            return new WebMapImagePixels(image.width(), image.height(), image.copyPixels());
        }
        if (entry == null || entry.item().isEmpty() || !RenderSystem.isOnRenderThread()) return null;
        Published captured = itemIcon(mc, entry.id(), entry.item());
        if (captured == null) return null;
        WebMapSnapshot.Image image = captured.image();
        return new WebMapImagePixels(image.width(), image.height(), image.copyPixels());
    }

    public static void clear() {
        CACHE.clear(); latest.clear(); generation = Long.MIN_VALUE; captureWindowStart = Long.MIN_VALUE;
        itemCaptureBudget = 0;
    }

    private static Published itemIcon(Minecraft mc, String itemId, ItemStack stack) {
        if (!RenderSystem.isOnRenderThread()) return null;
        syncGeneration(); refreshCaptureWindow();
        String source = "item:" + itemId + ":g" + generation;
        if (itemCaptureBudget <= 0 && !CACHE.hasImage(source, generation)) return null;
        WebMapSnapshot.Image image = CACHE.get(source, generation, () -> {
            if (itemCaptureBudget <= 0) return null;
            itemCaptureBudget--;
            return renderItem(mc, stack);
        });
        return image == null ? null : new Published(WebMapIconCache.opaqueKey(source), image);
    }

    private static Captured texture(Minecraft mc, String identity, ResourceLocation location,
                                    int u, int v, int width, int height, int textureWidth, int textureHeight,
                                    boolean face) {
        if (!RenderSystem.isOnRenderThread()) return null;
        syncGeneration(); refreshCaptureWindow();
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
            return image == null ? null : new Captured(source, image);
        }

        AbstractTexture texture;
        try { texture = mc.getTextureManager().getTexture(location); }
        catch (RuntimeException unavailable) { return null; }
        if (texture instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
            String source = "dynamic:" + identity + ":" + location + ":" + System.identityHashCode(dynamic)
                    + ":" + u + ":" + v + ":" + width + ":" + height + ":g" + generation;
            WebMapSnapshot.Image image = CACHE.get(source, generation, () -> {
                NativeImage nativeImage = dynamic.getPixels();
                if (nativeImage == null) return null;
                int sourceWidth = nativeImage.getWidth(), sourceHeight = nativeImage.getHeight();
                if (sourceWidth <= 0 || sourceHeight <= 0 || sourceWidth > 8192 || sourceHeight > 8192
                        || (long) sourceWidth * sourceHeight > 16_777_216L) return null;
                int[] abgr = new int[sourceWidth * sourceHeight];
                for (int y = 0; y < sourceHeight; y++) for (int x = 0; x < sourceWidth; x++) {
                    abgr[y * sourceWidth + x] = nativeImage.getPixelRGBA(x, y);
                }
                WebMapImagePixels pixels = WebMapImagePixels.fromNativeAbgr(sourceWidth, sourceHeight, abgr);
                return face ? pixels.minecraftFace() : pixels.textureRegion(u, v, width, height, textureWidth, textureHeight);
            });
            return image == null ? null : new Captured(source, image);
        }
        if (!(texture instanceof HttpTexture httpTexture)) {
            String pending = "pending:" + identity + ":" + location + ":g" + generation;
            CACHE.get(pending, generation, () -> null);
            return null;
        }
        String source = "http:" + identity + ":" + location + ":" + System.identityHashCode(httpTexture)
                + ":" + u + ":" + v + ":" + width + ":" + height + ":g" + generation;
        WebMapSnapshot.Image image = CACHE.get(source, generation, HTTP_TEXTURE_REFRESH_NANOS, () -> {
            WebMapImagePixels pixels = gpuTexturePixels(httpTexture);
            if (pixels == null) return null;
            return face ? pixels.minecraftFace() : pixels.textureRegion(u, v, width, height, textureWidth, textureHeight);
        });
        return image == null ? null : new Captured(source, image);
    }

    /** Copies only an Emojiful frame already validated as uploaded by MarkerIconCatalog. */
    private static Captured emojiFrame(Minecraft mc, MarkerIconCatalog.Entry entry,
                                       MarkerIconCatalog.Texture frame) {
        if (frame == null || !RenderSystem.isOnRenderThread()) return null;
        AbstractTexture texture;
        try { texture = mc.getTextureManager().getTexture(frame.location()); }
        catch (RuntimeException unavailable) { return null; }
        String source = "emoji-frame:" + entry.id() + ":" + frame.location() + ":"
                + System.identityHashCode(texture) + ":gl" + texture.getId()
                + ":" + frame.u() + ":" + frame.v()
                + ":" + frame.width() + ":" + frame.height() + ":g" + generation;
        WebMapSnapshot.Image image = CACHE.get(source, generation, () -> {
            WebMapImagePixels pixels = gpuTexturePixels(texture, 1024, 1_048_576L);
            return pixels == null ? null : pixels.textureRegion(frame.u(), frame.v(), frame.width(),
                    frame.height(), frame.textureWidth(), frame.textureHeight());
        });
        return image == null ? null : new Captured(source, image);
    }

    private static WebMapImagePixels gpuTexturePixels(AbstractTexture texture) {
        return gpuTexturePixels(texture, 8192, 16_777_216L);
    }

    private static WebMapImagePixels gpuTexturePixels(AbstractTexture texture, int maxDimension, long maxPixels) {
        int textureId = texture.getId();
        if (textureId <= 0) return null;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        RenderSystem.bindTexture(textureId);
        try {
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension
                    || (long) width * height > maxPixels) return null;
            return withSafePixelPackState(() -> {
                try (NativeImage downloaded = new NativeImage(width, height, false)) {
                    // NativeImage.downloadTexture's first parameter is the mip level,
                    // not the OpenGL texture id. The texture is already bound above.
                    downloaded.downloadTexture(0, false);
                    int[] abgr = new int[width * height];
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        abgr[y * width + x] = downloaded.getPixelRGBA(x, y);
                    }
                    return WebMapImagePixels.fromNativeAbgr(width, height, abgr);
                }
            });
        } finally {
            RenderSystem.bindTexture(previous);
        }
    }

    private static WebMapImagePixels renderItem(Minecraft mc, ItemStack stack) throws Exception {
        if (mc.level == null || stack.isEmpty()) return null;
        final int size = 32;
        // Save the active GL target before TextureTarget allocates/binds anything.
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int viewportX = viewport.get(0), viewportY = viewport.get(1);
        int viewportWidth = viewport.get(2), viewportHeight = viewport.get(3);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int textureUnitCount = Math.max(1, Math.min(MAX_CAPTURED_TEXTURE_UNITS,
                GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)));
        int[] textureBindings = new int[textureUnitCount];
        for (int unit = 0; unit < textureUnitCount; unit++) {
            activateTextureUnit(unit, textureUnitCount);
            textureBindings[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        activateTexture(activeTexture, textureUnitCount);
        int[] shaderTextures = new int[MAX_CAPTURED_TEXTURE_UNITS];
        for (int unit = 0; unit < shaderTextures.length; unit++) {
            shaderTextures[unit] = RenderSystem.getShaderTexture(unit);
        }
        ShaderInstance shader = RenderSystem.getShader();
        int currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        java.nio.IntBuffer scissorBox = org.lwjgl.BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        int scissorX = scissorBox.get(0), scissorY = scissorBox.get(1);
        int scissorWidth = scissorBox.get(2), scissorHeight = scissorBox.get(3);
        java.nio.ByteBuffer colorMask = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        boolean colorRed = colorMask.get(0) != 0, colorGreen = colorMask.get(1) != 0;
        boolean colorBlue = colorMask.get(2) != 0, colorAlpha = colorMask.get(3) != 0;
        var modelView = RenderSystem.getModelViewStack();
        boolean modelViewPushed = false;
        RenderSystem.backupProjectionMatrix();
        TextureTarget target = null;
        try {
            forceScissorState(false, scissorX, scissorY, scissorWidth, scissorHeight);
            forceColorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            target = new TextureTarget(size, size, true, true);
            target.setClearColor(0, 0, 0, 0);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().ortho(0, size, size, 0, -1024, 1024),
                    VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            modelView.pushMatrix(); modelViewPushed = true; modelView.identity();
            RenderSystem.applyModelViewMatrix();
            PoseStack pose = new PoseStack();
            pose.translate(size / 2.0, size / 2.0, 0);
            pose.scale(28, -28, 28);
            try (ByteBufferBuilder itemBuffer = new ByteBufferBuilder(4096)) {
                var buffers = MultiBufferSource.immediate(itemBuffer);
                mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
                buffers.endBatch();
            }
            RenderTarget screenshotTarget = target;
            try (NativeImage screenshot = withSafePixelPackState(() -> Screenshot.takeScreenshot(screenshotTarget))) {
                int sourceWidth = screenshot.getWidth(), sourceHeight = screenshot.getHeight();
                int[] abgr = new int[sourceWidth * sourceHeight];
                for (int y = 0; y < sourceHeight; y++) for (int x = 0; x < sourceWidth; x++) {
                    abgr[y * sourceWidth + x] = screenshot.getPixelRGBA(x, y);
                }
                return WebMapImagePixels.fromNativeAbgr(sourceWidth, sourceHeight, abgr);
            }
        } finally {
            if (target != null) target.destroyBuffers();
            RenderSystem.restoreProjectionMatrix();
            if (modelViewPushed) { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
            RenderSystem.setShader(() -> shader);
            for (int unit = 0; unit < shaderTextures.length; unit++) {
                RenderSystem.setShaderTexture(unit, shaderTextures[unit]);
            }
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            if (depthEnabled) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blendEnabled) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cullEnabled) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthFunc(depthFunction); RenderSystem.depthMask(depthMask);
            RenderSystem.blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            forceScissorState(scissorEnabled, scissorX, scissorY, scissorWidth, scissorHeight);
            forceColorMask(colorRed, colorGreen, colorBlue, colorAlpha);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            RenderSystem.viewport(viewportX, viewportY, viewportWidth, viewportHeight);
            for (int unit = 0; unit < textureUnitCount; unit++) {
                activateTextureUnit(unit, textureUnitCount);
                RenderSystem.bindTexture(textureBindings[unit]);
            }
            activateTexture(activeTexture, textureUnitCount);
            GlStateManager._glUseProgram(currentProgram);
        }
    }

    /** Force the RenderSystem active-unit cache and the GL context onto the requested unit. */
    private static void activateTextureUnit(int unit, int unitCount) {
        activateTexture(GL13.GL_TEXTURE0 + unit, unitCount);
    }

    private static void activateTexture(int texture, int unitCount) {
        int unit = texture - GL13.GL_TEXTURE0;
        int alternate = unit == 0 ? Math.min(1, unitCount - 1) : 0;
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + alternate);
        RenderSystem.activeTexture(texture);
    }

    /** Toggle through both states so stale cached state cannot leave scissoring enabled. */
    private static void forceScissorState(boolean enabled, int x, int y, int width, int height) {
        RenderSystem.enableScissor(x, y, width, height);
        RenderSystem.disableScissor();
        if (enabled) RenderSystem.enableScissor(x, y, width, height);
    }

    /** Flip the cached color mask before applying the requested mask to keep cache and GL aligned. */
    private static void forceColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        RenderSystem.colorMask(!red, !green, !blue, !alpha);
        RenderSystem.colorMask(red, green, blue, alpha);
    }

    private static <T> T withSafePixelPackState(java.util.function.Supplier<T> operation) {
        int[] parameters = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS,
                GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES, GL11.GL_PACK_LSB_FIRST,
                GL12.GL_PACK_IMAGE_HEIGHT, GL12.GL_PACK_SKIP_IMAGES};
        int[] previous = new int[parameters.length];
        for (int i = 0; i < parameters.length; i++) previous[i] = GL11.glGetInteger(parameters[i]);
        int previousPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            RenderSystem.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            for (int i = 1; i < parameters.length; i++) GlStateManager._pixelStore(parameters[i], 0);
            return operation.get();
        } finally {
            // NativeImage sets GL_PACK_ALIGNMENT and leaves it changed. Nonzero row
            // lengths, skips, or a pixel-pack buffer also corrupt CPU-pointer reads.
            for (int i = 0; i < parameters.length; i++) GlStateManager._pixelStore(parameters[i], previous[i]);
            RenderSystem.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPackBuffer);
        }
    }

    private record Captured(String source, WebMapSnapshot.Image image) {}

    private static Published published(Captured captured) {
        return captured == null ? null : new Published(WebMapIconCache.opaqueKey(captured.source()), captured.image());
    }

    private static Published remember(String identity, Published current) {
        if (current != null) {
            latest.put(identity, current);
            while (latest.size() > 512) latest.remove(latest.keySet().iterator().next());
            return current;
        }
        return latest(identity);
    }

    private static Published latest(String identity) { return latest.get(identity); }

    private static void syncGeneration() { generation = MarkerIconCatalog.resourceGeneration(); }

    private static void refreshCaptureWindow() {
        long now = System.nanoTime();
        if (captureWindowStart != Long.MIN_VALUE && now - captureWindowStart < CAPTURE_WINDOW_NANOS) return;
        captureWindowStart = now;
        itemCaptureBudget = MAX_ITEM_CAPTURES_PER_SECOND;
        CACHE.beginPublish(16);
    }
}
