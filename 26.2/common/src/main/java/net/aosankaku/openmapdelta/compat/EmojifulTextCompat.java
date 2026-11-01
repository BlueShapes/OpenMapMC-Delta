// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import net.minecraft.util.FormattedCharSequence;

import java.lang.reflect.Method;

/** Optional display-only API: omit emoji images from text halo passes while preserving their advance. */
public final class EmojifulTextCompat {
    private static final Method OUTLINE = findOutlineMethod();

    private EmojifulTextCompat() {}

    public static FormattedCharSequence outlineSequence(FormattedCharSequence source) {
        if (source == null || OUTLINE == null) return source;
        try {
            Object result = OUTLINE.invoke(null, source);
            return result instanceof FormattedCharSequence sequence ? sequence : source;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return source;
        }
    }

    private static Method findOutlineMethod() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        ClassLoader own = EmojifulTextCompat.class.getClassLoader();
        Method method = findOutlineMethod(context);
        return method != null || context == own ? method : findOutlineMethod(own);
    }

    private static Method findOutlineMethod(ClassLoader loader) {
        if (loader == null) return null;
        try {
            Class<?> api = Class.forName("com.hrznstudio.emojiful.render.EmojiFontHelper", false, loader);
            return api.getMethod("outlineSequence", FormattedCharSequence.class);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }
}
