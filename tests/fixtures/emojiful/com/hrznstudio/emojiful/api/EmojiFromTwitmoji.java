package com.hrznstudio.emojiful.api;

public final class EmojiFromTwitmoji extends Emoji {
    public EmojiFromTwitmoji(String name, String... aliases) { super(name, aliases); }

    public static final class DownloadImageData extends Emoji.DownloadImageData {
        private boolean textureUploaded;
        public DownloadImageData(boolean uploaded) {
            super(true);
            textureUploaded = uploaded;
        }
    }
}
