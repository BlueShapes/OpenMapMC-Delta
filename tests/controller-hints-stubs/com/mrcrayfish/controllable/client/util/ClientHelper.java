package com.mrcrayfish.controllable.client.util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
public final class ClientHelper {
    public static int theme;
    public static MutableComponent getButtonComponent(int button) {
        MutableComponent result=Component.literal("test.glyph."+theme+"."+button);
        result.buttonGlyph=true;
        return result;
    }
}
