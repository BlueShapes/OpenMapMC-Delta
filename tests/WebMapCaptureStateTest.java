// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import com.mojang.blaze3d.vertex.PoseStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/** Headless real-GL regression test for WebMapTextureCapture's private item capture path. */
public final class WebMapCaptureStateTest {
    private static final int WINDOW_SIZE = 128;
    private static final List<String> failures = new ArrayList<>();
    private static Unsafe unsafe;
    private static long window;
    private static int drawFramebuffer;
    private static int readFramebuffer;
    private static final int[] textureBindings = new int[12];
    private static final int originalActiveTextureIndex = 2;
    private static final int otherActiveTextureIndex = 5;
    private static int drawFramebufferTexture;
    private static int readFramebufferTexture;
    private static int originalProgram;
    private static int alteredProgram;

    private WebMapCaptureStateTest() {}

    public static void main(String[] args) throws Exception {
        String sharedLibraryDirectory = args.length == 0 ? null : args[0];
        if (sharedLibraryDirectory != null) {
            System.setProperty("org.lwjgl.system.SharedLibraryExtractPath", sharedLibraryDirectory);
        }

        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err).set();
        try {
            require(GLFW.glfwInit(), "GLFW initializes");
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            window = GLFW.glfwCreateWindow(WINDOW_SIZE, WINDOW_SIZE, "Odyssey hidden GL test", 0L, 0L);
            require(window != 0L, "hidden GLFW window is created");
            require(GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "GL context window remains hidden");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            initializeRenderSystem();
            initializeMinecraftStubs();
            initializeGlStateObjects();

            verifySuccessfulCaptureRestoresGlState();
            verifyFailedCaptureRestoresGlState();
            verifyEmojiTextureReadback();

            if (!failures.isEmpty()) {
                throw new AssertionError("WebMapTextureCapture state regressions:\n - " + String.join("\n - ", failures));
            }
            System.out.println("WebMapCaptureStateTest: hidden GLFW item capture, emoji pixels, bounds, and GL state checks passed.");
        } finally {
            if (window != 0L) {
                GLFW.glfwMakeContextCurrent(window);
                if (GL.getCapabilities() != null) {
                    if (drawFramebuffer != 0) GL30.glDeleteFramebuffers(drawFramebuffer);
                    if (readFramebuffer != 0) GL30.glDeleteFramebuffers(readFramebuffer);
                    for (int texture : textureBindings) if (texture != 0) GL11.glDeleteTextures(texture);
                    if (drawFramebufferTexture != 0) GL11.glDeleteTextures(drawFramebufferTexture);
                    if (readFramebufferTexture != 0) GL11.glDeleteTextures(readFramebufferTexture);
                    if (originalProgram != 0) GL20.glDeleteProgram(originalProgram);
                    if (alteredProgram != 0) GL20.glDeleteProgram(alteredProgram);
                }
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
            errorCallback.free();
        }
    }

    private static void initializeRenderSystem() {
        RenderSystem.initRenderThread();
        RenderSystem.initRenderer(0, false);
        RenderSystem.setupDefaultState(0, 0, WINDOW_SIZE, WINDOW_SIZE);
        RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().identity(), VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.finishInitialization();
    }

    private static void initializeMinecraftStubs() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        unsafe = (Unsafe) unsafeField.get(null);

        net.minecraft.SharedConstants.tryDetectVersion();
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError forgeEventBootstrapFailure) {
            if (!isStandaloneForgeEventbusFailure(forgeEventBootstrapFailure)) throw forgeEventBootstrapFailure;
            // Bootstrap has already registered the vanilla built-in registries. The optional Forge network
            // listener registration needs FML's launch-time class transformation, which this headless harness
            // intentionally does not start.
            System.out.println("WebMapCaptureStateTest: vanilla registries ready; skipped standalone Forge NetworkHooks init.");
        }

        Minecraft mc = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        ClientLevel level = (ClientLevel) unsafe.allocateInstance(ClientLevel.class);
        mc.level = level;
        ItemRenderer renderer = (ItemRenderer) unsafe.allocateInstance(FakeItemRenderer.class);
        Field itemRendererField = Minecraft.class.getDeclaredField("itemRenderer");
        unsafe.putObject(mc, unsafe.objectFieldOffset(itemRendererField), renderer);
        TEST_MINECRAFT = mc;
        TEST_STACK = new ItemStack(Items.STICK);
        require(!TEST_STACK.isEmpty(), "real registered ItemStack is nonempty");
    }

    private static Minecraft TEST_MINECRAFT;
    private static ItemStack TEST_STACK;

    private static boolean isStandaloneForgeEventbusFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof NoSuchMethodException
                    && "net.minecraftforge.network.NetworkEvent.<init>()".equals(cause.getMessage())) return true;
        }
        return false;
    }

    private static void initializeGlStateObjects() {
        drawFramebuffer = framebuffer();
        readFramebuffer = framebuffer();
        for (int i = 0; i < textureBindings.length; i++) textureBindings[i] = texture();
        originalProgram = program();
        alteredProgram = program();
    }

    private static int framebuffer() {
        int id = GL30.glGenFramebuffers();
        int texture = texture();
        if (drawFramebufferTexture == 0) drawFramebufferTexture = texture;
        else readFramebufferTexture = texture;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE,
                "test framebuffer is complete");
        return id;
    }

    private static int texture() {
        int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 2, 2, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return id;
    }

    private static int program() {
        String vertexSource = "#version 150 core\nvoid main(){ gl_Position = vec4(0.0); }";
        String fragmentSource = "#version 150 core\nout vec4 color; void main(){ color = vec4(1.0); }";
        int vertex = shader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = shader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE,
                "test shader program links: " + GL20.glGetProgramInfoLog(program));
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        return program;
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        require(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE,
                "test shader compiles: " + GL20.glGetShaderInfoLog(shader));
        return shader;
    }

    private static void verifySuccessfulCaptureRestoresGlState() throws Exception {
        configureCallerState();
        FakeItemRenderer.throwAfterDraw = false;
        WebMapImagePixels pixels = invokeRenderItem();
        require(pixels != null && pixels.width() == 32 && pixels.height() == 32,
                "successful capture returns a 32x32 image");
        if (pixels != null) {
            int color = pixels.pixels()[16 * pixels.width() + 16];
            require(red(color) > 220 && green(color) > 35 && green(color) < 70
                            && blue(color) < 50 && alpha(color) > 240,
                    "captured center pixel is the known opaque red marker (ABGR 0x" + Integer.toHexString(color) + ")");
        }
        assertCallerState("success");
    }

    private static void verifyFailedCaptureRestoresGlState() throws Exception {
        configureCallerState();
        FakeItemRenderer.throwAfterDraw = true;
        try {
            invokeRenderItem();
            failures.add("renderer exception is propagated to the caller");
        } catch (ExpectedRenderFailure expected) {
            // The state assertions below verify the finally path after a renderer failure.
        }
        FakeItemRenderer.throwAfterDraw = false;
        assertCallerState("exception");
    }

    private static WebMapImagePixels invokeRenderItem() throws Exception {
        Method renderItem = WebMapTextureCapture.class.getDeclaredMethod("renderItem", Minecraft.class, ItemStack.class);
        renderItem.setAccessible(true);
        try {
            return (WebMapImagePixels) renderItem.invoke(null, TEST_MINECRAFT, TEST_STACK);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof ExpectedRenderFailure expected) throw expected;
            if (cause instanceof Exception exception) throw exception;
            if (cause instanceof Error error) throw error;
            throw new AssertionError("unexpected renderer failure", cause);
        }
    }

    private static void verifyEmojiTextureReadback() throws Exception {
        NativeImage source = new NativeImage(2, 2, false);
        int[] expected = {0x80402010, 0xFF0000FF, 0xFF00FF00, 0x40806020};
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) source.setPixelRGBA(x, y, expected[y * 2 + x]);
        DynamicTexture texture = new DynamicTexture(source);
        int packBuffer = GL15.glGenBuffers();
        try {
            configureCallerState();
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, 256L, GL15.GL_STREAM_READ);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 8);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 7);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 2);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 3);
            Method capture = WebMapTextureCapture.class.getDeclaredMethod("gpuTexturePixels", AbstractTexture.class, int.class, long.class);
            capture.setAccessible(true);
            WebMapImagePixels pixels = (WebMapImagePixels) capture.invoke(null, texture, 1024, 1_048_576L);
            require(pixels != null && java.util.Arrays.equals(pixels.pixels(), expected),
                    "emoji GPU readback preserves source row order and straight alpha");
            require(capture.invoke(null, texture, 1, 1_048_576L) == null,
                    "emoji readback rejects oversized dimensions before allocation");
            require(capture.invoke(null, texture, 1024, 3L) == null,
                    "emoji readback rejects oversized pixel counts before allocation");
            require(GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING) == packBuffer,
                    "emoji readback restores the caller's pixel-pack buffer");
            require(GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT) == 8
                            && GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH) == 7
                            && GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS) == 2
                            && GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS) == 3,
                    "emoji readback restores row layout, skips, and alignment");
            assertCallerState("emoji readback");
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL15.glDeleteBuffers(packBuffer);
            texture.close();
        }
    }

    private static void configureCallerState() {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        RenderSystem.viewport(9, 11, 73, 67);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.colorMask(false, true, true, false);
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_REVERSE_SUBTRACT);
        for (int unit = 0; unit < textureBindings.length; unit++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
            RenderSystem.bindTexture(textureBindings[unit]);
            RenderSystem.setShaderTexture(unit, textureBindings[unit]);
        }
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + originalActiveTextureIndex);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(90, 91, 12, 13);
        GL20.glUseProgram(originalProgram);
    }

    private static void assertCallerState(String phase) {
        require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == drawFramebuffer,
                phase + " restores draw framebuffer");
        require(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == readFramebuffer,
                phase + " restores read framebuffer");
        IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        require(viewport.get(0) == 9 && viewport.get(1) == 11 && viewport.get(2) == 73 && viewport.get(3) == 67,
                phase + " restores the custom viewport");
        require(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), phase + " restores depth-test enablement");
        require(!GL11.glIsEnabled(GL11.GL_BLEND), phase + " restores blend enablement");
        require(GL11.glIsEnabled(GL11.GL_CULL_FACE), phase + " restores cull enablement");
        require(GL11.glGetInteger(GL11.GL_DEPTH_FUNC) == GL11.GL_LEQUAL,
                phase + " restores depth function");
        require(!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), phase + " restores depth write mask");
        ByteBufferState colorMask = colorMask();
        require(!colorMask.red && colorMask.green && colorMask.blue && !colorMask.alpha,
                phase + " restores color write masks");
        require(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB) == GL11.GL_SRC_ALPHA
                        && GL11.glGetInteger(GL14.GL_BLEND_DST_RGB) == GL11.GL_ONE_MINUS_SRC_ALPHA
                        && GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA) == GL11.GL_ONE
                        && GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA) == GL11.GL_ZERO,
                phase + " restores blend factors");
        require(GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB) == GL14.GL_FUNC_ADD
                        && GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA) == GL14.GL_FUNC_REVERSE_SUBTRACT,
                phase + " restores blend equations");
        require(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), phase + " restores scissor-test enablement");
        IntBuffer scissor = org.lwjgl.BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        require(scissor.get(0) == 90 && scissor.get(1) == 91 && scissor.get(2) == 12 && scissor.get(3) == 13,
                phase + " restores scissor box");
        require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE0 + originalActiveTextureIndex,
                phase + " restores the active texture unit");
        for (int unit = 0; unit < textureBindings.length; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            require(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == textureBindings[unit],
                    phase + " restores texture unit " + unit + " binding");
            require(RenderSystem.getShaderTexture(unit) == textureBindings[unit],
                    phase + " restores shader texture slot " + unit);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + originalActiveTextureIndex);
        require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == originalProgram,
                phase + " restores the current shader program");
    }

    private record ByteBufferState(boolean red, boolean green, boolean blue, boolean alpha) {}

    private static ByteBufferState colorMask() {
        java.nio.ByteBuffer mask = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
        return new ByteBufferState(mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0);
    }

    private static int red(int abgr) { return abgr & 255; }
    private static int green(int abgr) { return (abgr >>> 8) & 255; }
    private static int blue(int abgr) { return (abgr >>> 16) & 255; }
    private static int alpha(int abgr) { return (abgr >>> 24) & 255; }

    private static void require(boolean condition, String message) {
        if (!condition) failures.add(message);
    }

    private static final class ExpectedRenderFailure extends RuntimeException {}

    private static final class FakeItemRenderer extends ItemRenderer {
        private static boolean throwAfterDraw;

        private FakeItemRenderer() { super(null, null, null, null, null); }

        @Override
        public void renderStatic(ItemStack stack, ItemDisplayContext context, int light, int overlay,
                                 PoseStack pose, MultiBufferSource buffers, Level level, int seed) {
            require(context == ItemDisplayContext.GUI, "capture renders in GUI item context");
            org.joml.Matrix4f transform = pose.last().pose();
            require(Math.abs(transform.m00() - 28f) < .001f && Math.abs(transform.m11() + 28f) < .001f
                            && Math.abs(transform.m22() - 28f) < .001f,
                    "capture uses a centered, y-flipped 28x item pose");
            require(Math.abs(transform.m30() - 16f) < .001f && Math.abs(transform.m31() - 16f) < .001f,
                    "capture centers the item in the 32px target");
            java.nio.FloatBuffer depth = org.lwjgl.BufferUtils.createFloatBuffer(1);
            GL11.glReadPixels(16, 16, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            require(Math.abs(depth.get(0) - 1f) < .001f,
                    "temporary render target depth buffer is initialized to 1.0 despite caller depthMask=false");
            GL11.glClearColor(.9f, .2f, .1f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            RenderSystem.viewport(3, 5, 17, 19);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthFunc(GL11.GL_GREATER);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(false, true, false, false);
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ZERO, GL11.GL_ONE);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_REVERSE_SUBTRACT, GL14.GL_FUNC_ADD);
            for (int unit : new int[]{0, 1, 2, 5}) {
                RenderSystem.setShaderTexture(unit, textureBindings[otherActiveTextureIndex]);
            }
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + otherActiveTextureIndex);
            GL20.glUseProgram(alteredProgram);
            if (throwAfterDraw) throw new ExpectedRenderFailure();
        }
    }
}
