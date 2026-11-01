// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

/** Pure-JDK, versioned disk storage for fixed-size map tiles. */
public final class TileStorage {
    private static final int MAGIC = 0x4F4D5443; // OMTC
    private static final int FORMAT_VERSION = 3;
    private static final int MAX_IDENTITY_BYTES = 32 * 1024;
    private static final int MAX_FILE_BYTES = 384 * 1024;
    private static final int MAX_UNCOMPRESSED_BYTES = 16 + 2 * (4 + MAX_IDENTITY_BYTES)
            + Tile.PIXEL_COUNT * Integer.BYTES * 3 + (Tile.PIXEL_COUNT / Long.SIZE) * Long.BYTES;

    private final Path cacheRoot;
    private final String worldIdentity;
    private final String dimensionIdentity;
    private final Path dimensionDirectory;

    public TileStorage(Path cacheRoot, String worldIdentity, String dimensionIdentity) {
        if (worldIdentity == null || worldIdentity.isBlank()) {
            throw new IllegalArgumentException("worldIdentity must be stable and non-empty");
        }
        if (dimensionIdentity == null || dimensionIdentity.isBlank()) {
            throw new IllegalArgumentException("dimensionIdentity must be non-empty");
        }
        this.cacheRoot = cacheRoot.toAbsolutePath().normalize();
        this.worldIdentity = worldIdentity;
        this.dimensionIdentity = dimensionIdentity;
        Path worldDirectory = this.cacheRoot.resolve(sha256(worldIdentity));
        this.dimensionDirectory = worldDirectory.resolve(sha256(dimensionIdentity));
    }

    public Path fileFor(int alignedX, int alignedZ) {
        requireAligned(alignedX, alignedZ);
        return dimensionDirectory.resolve(alignedX + "_" + alignedZ + ".tile.z");
    }

    /** Lists well-formed tile filenames without opening or modifying their contents. */
    public List<StoredTileCoordinate> listStoredTiles() {
        List<StoredTileCoordinate> stored = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dimensionDirectory, "*.tile.z")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                String coordinateText = name.substring(0, name.length() - ".tile.z".length());
                int separator = coordinateText.lastIndexOf('_');
                if (separator <= 0 || separator == coordinateText.length() - 1) continue;
                try {
                    stored.add(new StoredTileCoordinate(
                            Integer.parseInt(coordinateText.substring(0, separator)),
                            Integer.parseInt(coordinateText.substring(separator + 1))));
                } catch (IllegalArgumentException ignored) {
                    // Unrecognized filenames are left untouched and never scheduled for reads.
                }
            }
        } catch (IOException | SecurityException | DirectoryIteratorException ignored) {
            // A missing/unreadable directory is equivalent to no discoverable cached tiles.
        }
        stored.sort(Comparator.comparingInt(StoredTileCoordinate::alignedX)
                .thenComparingInt(StoredTileCoordinate::alignedZ));
        return List.copyOf(stored);
    }

    public Optional<Tile.Snapshot> load(int alignedX, int alignedZ) {
        Path file = fileFor(alignedX, alignedZ);
        byte[] compressed;
        try (var input = Files.newInputStream(file)) {
            compressed = input.readNBytes(MAX_FILE_BYTES + 1);
        } catch (IOException ignored) {
            return Optional.empty();
        }
        if (compressed.length > MAX_FILE_BYTES) return Optional.empty();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(decompressSingle(compressed)))) {
            if (in.readInt() != MAGIC) return Optional.empty();
            int version = in.readInt();
            if (version < 1 || version > FORMAT_VERSION) return Optional.empty();
            if (in.readInt() != alignedX || in.readInt() != alignedZ) return Optional.empty();
            if (!worldIdentity.equals(readString(in)) || !dimensionIdentity.equals(readString(in))) {
                return Optional.empty();
            }
            int[] pixels = new int[Tile.PIXEL_COUNT];
            for (int i = 0; i < pixels.length; i++) pixels[i] = in.readInt();
            long[] observed = new long[Tile.PIXEL_COUNT / Long.SIZE];
            for (int i = 0; i < observed.length; i++) observed[i] = in.readLong();
            int[] heights = new int[Tile.PIXEL_COUNT];
            java.util.Arrays.fill(heights, Tile.UNKNOWN_HEIGHT);
            if (version >= 2) {
                for (int i = 0; i < heights.length; i++) heights[i] = in.readInt();
            }
            int[] grassPixels = null;
            if (version >= 3) {
                grassPixels = new int[Tile.PIXEL_COUNT];
                for (int i = 0; i < grassPixels.length; i++) grassPixels[i] = in.readInt();
            }
            if (in.read() != -1) return Optional.empty();
            Tile.Snapshot snapshot = new Tile.Snapshot(pixels, observed, heights, grassPixels);
            return snapshot.hasObservedPixels() ? Optional.of(snapshot) : Optional.empty();
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /** Merges live sampled pixels over existing cached data before atomically replacing the file. */
    public void saveMerged(int alignedX, int alignedZ, Tile.Snapshot sampled) throws IOException {
        requireAligned(alignedX, alignedZ);
        if (!sampled.hasObservedPixels()) return;
        Tile.Snapshot stored = load(alignedX, alignedZ).orElse(null);
        Tile.Snapshot merged = Tile.Snapshot.mergeObserved(stored, sampled);
        Path target = fileFor(alignedX, alignedZ);
        Files.createDirectories(dimensionDirectory);
        Path temporary = Files.createTempFile(dimensionDirectory, target.getFileName().toString(), ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(temporary))))) {
                out.writeInt(MAGIC);
                out.writeInt(FORMAT_VERSION);
                out.writeInt(alignedX);
                out.writeInt(alignedZ);
                writeString(out, worldIdentity);
                writeString(out, dimensionIdentity);
                for (int pixel : merged.copyPixels()) out.writeInt(pixel);
                for (long word : merged.copyObserved()) out.writeLong(word);
                for (int height : merged.copyHeights()) out.writeInt(height);
                for (int grass : merged.copyGrassPixels()) out.writeInt(grass);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public Path getCacheRoot() { return cacheRoot; }
    public String getWorldIdentity() { return worldIdentity; }
    public String getDimensionIdentity() { return dimensionIdentity; }

    private static byte[] decompressSingle(byte[] compressed) throws IOException {
        Inflater inflater = new Inflater();
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            inflater.setInput(compressed);
            while (!inflater.finished()) {
                int count;
                try {
                    count = inflater.inflate(buffer);
                } catch (DataFormatException malformed) {
                    throw new IOException("Malformed compressed tile", malformed);
                }
                if (count > 0) {
                    if (decoded.size() > MAX_UNCOMPRESSED_BYTES - count) {
                        throw new IOException("Tile payload exceeds the fixed format size");
                    }
                    decoded.write(buffer, 0, count);
                } else if (inflater.finished()) {
                    break;
                } else if (inflater.needsDictionary() || inflater.needsInput()) {
                    throw new EOFException("Truncated compressed tile");
                } else {
                    throw new IOException("Compressed tile decoder stalled");
                }
            }
            if (inflater.getRemaining() != 0) throw new IOException("Trailing bytes after compressed tile");
            return decoded.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static void requireAligned(int x, int z) {
        if ((x & (Tile.SIZE - 1)) != 0 || (z & (Tile.SIZE - 1)) != 0) {
            throw new IllegalArgumentException("Tile coordinates must be aligned to " + Tile.SIZE);
        }
    }

    public record StoredTileCoordinate(int alignedX, int alignedZ) {
        public StoredTileCoordinate {
            requireAligned(alignedX, alignedZ);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_IDENTITY_BYTES) throw new IOException("Cache identity is too long");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_IDENTITY_BYTES) throw new IOException("Invalid cache identity length");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }
}
