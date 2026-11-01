// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.web;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Encodes Minecraft's ABGR map colors without AWT or access to the render thread. */
public final class WebMapPng {
    private WebMapPng() {}
    public static byte[] encode(int[] pixels, int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > 512 || height > 512 || pixels.length != width * height) {
            throw new IllegalArgumentException("Invalid map image size");
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(result);
        out.writeLong(0x89504E470D0A1A0AL);
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream metadata = new DataOutputStream(header);
        metadata.writeInt(width); metadata.writeInt(height);
        metadata.write(new byte[]{8, 6, 0, 0, 0});
        chunk(out, "IHDR", header.toByteArray());
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            for (int y = 0; y < height; y++) {
                deflater.write(0);
                for (int x = 0; x < width; x++) {
                    int color = pixels[y * width + x];
                    deflater.write(color & 255); deflater.write((color >>> 8) & 255);
                    deflater.write((color >>> 16) & 255); deflater.write(color >>> 24);
                }
            }
        }
        chunk(out, "IDAT", compressed.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return result.toByteArray();
    }
    private static void chunk(DataOutputStream out, String type, byte[] data) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32(); crc.update(name); crc.update(data);
        out.writeInt(data.length); out.write(name); out.write(data); out.writeInt((int) crc.getValue());
    }
}
