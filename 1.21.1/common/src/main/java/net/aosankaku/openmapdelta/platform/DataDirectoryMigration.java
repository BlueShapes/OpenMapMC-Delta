// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.platform;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Safely moves this fork's settings and tile cache out of the legacy Odyssey directories. */
public final class DataDirectoryMigration {
    public static final String LEGACY_DIRECTORY = "odysseymap";
    public static final String DATA_DIRECTORY = "openmapdelta";
    public static final String CACHE_DIRECTORY = "openmapdelta";
    private static final String MARKER_NAME = ".legacy-data-migration-v1";
    private static final byte[] MARKER_CONTENT =
            "OpenMap Delta legacy data migration v1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final Set<RootPair> COMPLETED_ROOTS = new HashSet<>();

    private DataDirectoryMigration() {}

    /** Runs once for each config/game root pair; failures are deliberately not memoized. */
    public static void ensureMigrated(Path configDir, Path gameDir) throws IOException {
        ensureMigrated(configDir, gameDir, DataDirectoryMigration::moveDirectory);
    }

    static void ensureMigrated(Path configDir, Path gameDir, DirectoryMover directoryMover) throws IOException {
        Objects.requireNonNull(directoryMover, "directoryMover");
        RootPair roots = new RootPair(canonicalRoot(configDir, "configDir"), canonicalRoot(gameDir, "gameDir"));
        synchronized (COMPLETED_ROOTS) {
            if (COMPLETED_ROOTS.contains(roots)) return;
            migrate(roots, directoryMover);
            COMPLETED_ROOTS.add(roots);
        }
    }

    private static void migrate(RootPair roots, DirectoryMover directoryMover) throws IOException {
        Path configRoot = roots.configDir();
        Path gameRoot = roots.gameDir();
        Path legacyConfig = configRoot.resolve(LEGACY_DIRECTORY);
        Path newConfig = configRoot.resolve(DATA_DIRECTORY);
        Path legacyCache = legacyConfig.resolve("cache");
        Path newCache = gameRoot.resolve(CACHE_DIRECTORY);

        rejectOverlappingDataRoots(legacyConfig, newConfig, legacyCache, newCache);
        validateRoot(configRoot, "config directory");
        validateRoot(gameRoot, "game directory");
        validateExistingDirectory(newConfig, "new config directory");
        validateExistingDirectory(newCache, "new cache directory");
        validateExistingTree(newCache, "new cache directory");
        validateExistingTree(newConfig, "new config directory");

        Path marker = newConfig.resolve(MARKER_NAME);
        if (entryExists(marker)) {
            validateMarker(marker);
            return;
        }

        validateLegacyConfig(legacyConfig, legacyCache);
        copyTree(legacyConfig, newConfig, legacyCache, true);

        migrateCache(legacyCache, newCache, gameRoot, directoryMover);

        ensureDirectory(newConfig);
        publishMarker(marker);
    }

    private static void validateLegacyConfig(Path legacyConfig, Path legacyCache) throws IOException {
        if (!entryExists(legacyConfig)) return;
        requireDirectory(legacyConfig, "legacy config directory");
        if (entryExists(legacyCache)) {
            requireDirectory(legacyCache, "legacy cache directory");
            validateExistingTree(legacyCache, "legacy cache directory");
        }
        validateExistingTree(legacyConfig, "legacy config directory", legacyCache);
    }

    private static void migrateCache(Path legacyCache, Path newCache, Path gameRoot,
                                     DirectoryMover directoryMover) throws IOException {
        if (!entryExists(legacyCache)) return;
        requireDirectory(legacyCache, "legacy cache directory");
        validateExistingTree(legacyCache, "legacy cache directory");
        validateRoot(gameRoot, "game directory");
        ensureDirectory(gameRoot);

        if (!entryExists(newCache)) {
            try {
                directoryMover.move(legacyCache, newCache);
                return;
            } catch (FileAlreadyExistsException raced) {
                // A concurrent destination wins; merge the remaining legacy files below.
            } catch (IOException moveFailure) {
                if (!entryExists(legacyCache)) throw moveFailure;
                if (!entryExists(newCache)) ensureDirectory(newCache);
                try {
                    requireDirectory(newCache, "new cache directory");
                    validateExistingTree(newCache, "new cache directory");
                    copyTree(legacyCache, newCache, null, false);
                    return;
                } catch (IOException copyFailure) {
                    copyFailure.addSuppressed(moveFailure);
                    throw copyFailure;
                }
            }
        }

        requireDirectory(newCache, "new cache directory");
        validateExistingTree(newCache, "new cache directory");
        if (isEmptyDirectory(newCache)) {
            Files.delete(newCache);
            try {
                directoryMover.move(legacyCache, newCache);
                return;
            } catch (FileAlreadyExistsException raced) {
                requireDirectory(newCache, "new cache directory");
                validateExistingTree(newCache, "new cache directory");
            } catch (IOException moveFailure) {
                if (!entryExists(legacyCache)) throw moveFailure;
                if (!entryExists(newCache)) ensureDirectory(newCache);
                try {
                    requireDirectory(newCache, "new cache directory");
                    validateExistingTree(newCache, "new cache directory");
                    copyTree(legacyCache, newCache, null, false);
                    return;
                } catch (IOException copyFailure) {
                    copyFailure.addSuppressed(moveFailure);
                    throw copyFailure;
                }
            }
        }

        copyTree(legacyCache, newCache, null, false);
    }

    private static void copyTree(Path sourceRoot, Path destinationRoot, Path excludedSubtree,
                                 boolean skipMigrationMarker) throws IOException {
        if (!entryExists(sourceRoot)) return;
        requireDirectory(sourceRoot, "migration source");
        ensureDirectory(destinationRoot);
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isDirectory()) {
                    throw unsafeEntry(directory, "directory");
                }
                if (excludedSubtree != null && directory.equals(excludedSubtree)) return FileVisitResult.SKIP_SUBTREE;
                Path target = destinationRoot.resolve(sourceRoot.relativize(directory));
                ensureDirectory(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isRegularFile()) {
                    throw unsafeEntry(file, "regular file");
                }
                Path relative = sourceRoot.relativize(file);
                if (skipMigrationMarker && relative.getNameCount() == 1
                        && MARKER_NAME.equals(relative.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }
                Path target = destinationRoot.resolve(relative);
                Path parent = target.getParent();
                ensureDirectory(parent);
                if (entryExists(target)) {
                    requireRegularFile(target, "existing migration destination");
                    return FileVisitResult.CONTINUE;
                }
                copyFileWithoutReplacement(file, target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                throw new IOException("Cannot inspect migration path " + file, failure);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                if (excludedSubtree != null && directory.equals(excludedSubtree)) return FileVisitResult.CONTINUE;
                Path relative = sourceRoot.relativize(directory);
                Path target = destinationRoot.resolve(relative);
                if (!relative.toString().isEmpty() && entryExists(target)) {
                    requireDirectory(target, "existing migration destination");
                    Files.setLastModifiedTime(target, Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyFileWithoutReplacement(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        ensureDirectory(parent);
        Path temporary = Files.createTempFile(parent, ".openmapdelta-migration-", ".tmp");
        boolean sourceReadOnly;
        boolean published = false;
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            sourceReadOnly = isDefaultWindowsProvider(temporary) && isDosReadOnly(temporary);
            if (sourceReadOnly) setDosReadOnly(temporary, false);
            published = publishWithoutReplacement(temporary, target, "migration destination");
        } finally {
            Files.deleteIfExists(temporary);
        }
        if (published && sourceReadOnly) setDosReadOnly(target, true);
    }

    private static void publishMarker(Path marker) throws IOException {
        Path parent = marker.getParent();
        ensureDirectory(parent);
        if (entryExists(marker)) {
            validateMarker(marker);
            return;
        }
        Path temporary = Files.createTempFile(parent, ".openmapdelta-migration-marker-", ".tmp");
        try {
            Files.write(temporary, MARKER_CONTENT);
            publishWithoutReplacement(temporary, marker, "migration marker");
        } finally {
            Files.deleteIfExists(temporary);
        }
        validateMarker(marker);
    }

    private static void validateMarker(Path marker) throws IOException {
        requireRegularFile(marker, "migration marker");
        if (Files.size(marker) != MARKER_CONTENT.length
                || !java.util.Arrays.equals(Files.readAllBytes(marker), MARKER_CONTENT)) {
            throw new IOException("Invalid OpenMap Delta migration marker: " + marker);
        }
    }

    private static void validateExistingTree(Path root, String description) throws IOException {
        validateExistingTree(root, description, null);
    }

    private static void validateExistingTree(Path root, String description, Path excludedSubtree) throws IOException {
        if (!entryExists(root)) return;
        requireDirectory(root, description);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isDirectory()) {
                    throw unsafeEntry(directory, "directory");
                }
                if (excludedSubtree != null && directory.equals(excludedSubtree)) return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isRegularFile()) {
                    throw unsafeEntry(file, "regular file");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                throw new IOException("Cannot inspect " + description + " path " + file, failure);
            }
        });
    }

    private static boolean isEmptyDirectory(Path directory) throws IOException {
        try (var children = Files.newDirectoryStream(directory)) {
            return !children.iterator().hasNext();
        }
    }

    private static void ensureDirectory(Path directory) throws IOException {
        if (directory == null) throw new IOException("Migration destination has no parent directory");
        Path absolute = directory.toAbsolutePath().normalize();
        Path root = absolute.getRoot();
        if (root == null) throw new IOException("Migration path is not absolute: " + directory);
        Path current = root;
        for (Path part : absolute) {
            current = current.resolve(part);
            if (entryExists(current)) {
                requireDirectory(current, "migration directory");
            } else {
                try {
                    Files.createDirectory(current);
                } catch (FileAlreadyExistsException raced) {
                    requireDirectory(current, "migration directory");
                }
            }
        }
    }

    private static void validateRoot(Path root, String description) throws IOException {
        if (entryExists(root)) requireDirectory(root, description);
        else ensureDirectory(root);
    }

    private static void validateExistingDirectory(Path directory, String description) throws IOException {
        if (entryExists(directory)) requireDirectory(directory, description);
    }

    private static void requireDirectory(Path path, String description) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isDirectory()) {
            throw new IOException("Unsafe " + description + ": " + path);
        }
    }

    private static void requireRegularFile(Path path, String description) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isRegularFile()) {
            throw new IOException("Unsafe " + description + ": " + path);
        }
    }

    private static IOException unsafeEntry(Path path, String expected) {
        return new IOException("Unsafe migration entry; expected " + expected + ": " + path);
    }

    private static void rejectOverlappingDataRoots(Path legacyConfig, Path newConfig,
                                                   Path legacyCache, Path newCache) throws IOException {
        if (overlaps(legacyConfig, newConfig) || overlaps(newConfig, newCache)
                || overlaps(legacyConfig, newCache) || overlaps(legacyCache, newConfig)
                || overlaps(legacyCache, newCache)) {
            throw new IOException("Config and cache migration paths overlap; refusing to migrate data");
        }
    }

    private static boolean overlaps(Path left, Path right) {
        return left.startsWith(right) || right.startsWith(left);
    }

    private static boolean entryExists(Path path) throws IOException {
        try {
            Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException absent) {
            return false;
        }
    }

    private static boolean publishWithoutReplacement(Path temporary, Path target, String description) throws IOException {
        try {
            Files.createLink(target, temporary);
            return true;
        } catch (FileAlreadyExistsException raced) {
            requireRegularFile(target, "existing " + description);
            return false;
        } catch (IOException | UnsupportedOperationException linkFailure) {
            if (!isDefaultWindowsProvider(target)) {
                if (linkFailure instanceof IOException io) throw io;
                throw new IOException("Cannot safely publish " + description + ": " + target, linkFailure);
            }
            try {
                Files.move(temporary, target);
                return true;
            } catch (FileAlreadyExistsException raced) {
                requireRegularFile(target, "existing " + description);
                return false;
            } catch (IOException moveFailure) {
                moveFailure.addSuppressed(linkFailure);
                throw moveFailure;
            }
        }
    }

    private static boolean isDefaultWindowsProvider(Path path) {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                && path.getFileSystem().provider() == FileSystems.getDefault().provider();
    }

    private static boolean isDosReadOnly(Path path) throws IOException {
        DosFileAttributeView view = Files.getFileAttributeView(path, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        return view != null && view.readAttributes().isReadOnly();
    }

    private static void setDosReadOnly(Path path, boolean readOnly) throws IOException {
        DosFileAttributeView view = Files.getFileAttributeView(path, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view != null) view.setReadOnly(readOnly);
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        Files.move(source, target);
    }

    private static Path canonicalRoot(Path path, String name) throws IOException {
        if (path == null) throw new IOException(name + " is required for data migration");
        try {
            Path absolute = path.toAbsolutePath().normalize();
            if (!entryExists(absolute)) Files.createDirectories(absolute);
            return absolute.toRealPath();
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid " + name + " for data migration", invalid);
        }
    }

    private record RootPair(Path configDir, Path gameDir) {
        private RootPair {
            Objects.requireNonNull(configDir);
            Objects.requireNonNull(gameDir);
        }
    }

    @FunctionalInterface
    interface DirectoryMover {
        void move(Path source, Path target) throws IOException;
    }
}
