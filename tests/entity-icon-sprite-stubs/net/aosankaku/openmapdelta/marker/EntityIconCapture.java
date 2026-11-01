package net.aosankaku.openmapdelta.marker;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.util.function.Consumer;

public final class EntityIconCapture {
    private EntityIconCapture() {}
    public static void capture(Minecraft mc, Entity entity, boolean preferHead, Consumer<NativeImage> callback) {}
}
