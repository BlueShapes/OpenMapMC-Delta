package com.hrznstudio.emojiful.api;

import java.util.List;

/** Only the optional API contract; no image loading or Minecraft dependencies. */
public class Emoji {
    public static final String loading_texture = "emojiful:loading";
    public static final String noSignal_texture = "emojiful:no_signal";
    public static final String error_texture = "emojiful:error";
    public String name;
    public List<String> strings;
    public List<String> texts = List.of();
    public boolean worldBased;
    public Object frame = "emojiful:frame_0";
    public boolean failFrame;
    public int loadRequests;

    public Emoji(String name, String... aliases) {
        this.name = name;
        this.strings = List.of(aliases);
    }

    public Object getResourceLocationForBinding() {
        loadRequests++;
        if (failFrame) throw new IllegalStateException("download unavailable");
        return frame;
    }

    public static class DownloadImageData {
        public boolean textureUploaded;
        public DownloadImageData(boolean uploaded) { textureUploaded = uploaded; }
    }
}
