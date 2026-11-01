// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat.mapselection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Bounded, shape-preserving boolean edits for provider configuration files. */
final class MapModSelectionFiles {
    static final int MAX_CONFIG_BYTES = 1_048_576;

    private MapModSelectionFiles() {}

    static Path validatePath(Path allowedRoot, Path target) throws IOException {
        Path root = allowedRoot.toAbsolutePath().normalize();
        Path file = target.toAbsolutePath().normalize();
        if (!file.startsWith(root)) throw new IOException("Map provider config is outside its allowlisted root: " + file);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Allowlisted config root does not exist: " + root);
        Path realRoot = root.toRealPath();
        Path existing = file;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing == null || !existing.toRealPath().startsWith(realRoot)) {
            throw new IOException("Map provider config path escapes its allowlisted root: " + file);
        }
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(realRoot)) {
                throw new IOException("Map provider config file is a symlink or escapes its allowlisted root: " + file);
            }
            if (!Files.isRegularFile(file)) throw new IOException("Map provider config is not a regular file: " + file);
        }
        return file;
    }

    static Document document(Path allowedRoot, Path target, boolean lineConfig) throws IOException {
        return document(allowedRoot, target, lineConfig, " = ");
    }

    static Document document(Path allowedRoot, Path target, boolean lineConfig,
                             String missingLineSeparator) throws IOException {
        Path file = validatePath(allowedRoot, target);
        boolean exists = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
        byte[] original = exists ? readBounded(file, MAX_CONFIG_BYTES) : new byte[0];
        if (original.length > MAX_CONFIG_BYTES) throw new IOException("Map provider config exceeds size limit: " + file);
        return new Document(file, original, exists, lineConfig, missingLineSeparator);
    }

    static byte[] readBounded(Path file, int maxBytes) throws IOException {
        long size = Files.size(file);
        if (size > maxBytes) throw new IOException("Map provider config exceeds size limit: " + file);
        try (InputStream input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(maxBytes + 1);
            if (bytes.length > maxBytes || input.read() != -1) {
                throw new IOException("Map provider config exceeds size limit: " + file);
            }
            return bytes;
        }
    }

    static String decodeUtf8(byte[] bytes, Path file) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Provider config is not valid UTF-8: " + file, e);
        }
    }

    static final class Document {
        private final Path path;
        private final byte[] originalBytes;
        private final boolean existed;
        private final boolean lineConfig;
        private JsonObject json;
        private String[] lines;
        private final String lineSeparator;
        private final String missingLineSeparator;

        private Document(Path path, byte[] originalBytes, boolean existed, boolean lineConfig,
                         String missingLineSeparator) throws IOException {
            this.path = path;
            this.originalBytes = originalBytes;
            this.existed = existed;
            this.lineConfig = lineConfig;
            this.missingLineSeparator = missingLineSeparator;
            if (lineConfig && !" = ".equals(missingLineSeparator) && !":".equals(missingLineSeparator)) {
                throw new IllegalArgumentException("Unsupported line config separator");
            }
            String text = decodeUtf8(originalBytes, path);
            lineSeparator = text.contains("\r\n") ? "\r\n" : text.contains("\r") ? "\r" : "\n";
            if (lineConfig) {
                lines = text.split("\\r\\n|\\n|\\r", -1);
            } else if (text.isEmpty() && !existed) {
                json = new JsonObject();
            } else {
                try {
                    JsonElement parsed = JsonParser.parseString(text);
                    if (parsed == null || !parsed.isJsonObject()) throw new IOException("Config root must be a JSON object: " + path);
                    json = parsed.getAsJsonObject();
                } catch (JsonParseException e) {
                    throw new IOException("Malformed JSON provider config: " + path, e);
                }
            }
        }

        Path path() { return path; }
        byte[] originalBytes() { return originalBytes.clone(); }
        int originalSize() { return originalBytes.length; }
        boolean existed() { return existed; }

        Boolean readBoolean(String key) throws IOException {
            if (lineConfig) return readLineBoolean(lines, key, path);
            JsonElement element = json.get(key);
            return element == null ? null : readJsonBoolean(element, key, path);
        }

        void writeBoolean(String key, boolean value) throws IOException {
            if (lineConfig) {
                lines = writeLineBoolean(lines, key, value, path, missingLineSeparator);
                return;
            }
            JsonElement current = json.get(key);
            if (current == null) {
                json.addProperty(key, value);
            } else if (current.isJsonPrimitive()) {
                var primitive = current.getAsJsonPrimitive();
                if (primitive.isBoolean()) json.addProperty(key, value);
                else if (primitive.isString() && parseBoolean(primitive.getAsString()) != null) {
                    json.addProperty(key, Boolean.toString(value));
                } else throw invalidBoolean(key, path);
            } else if (current.isJsonObject()) {
                JsonObject object = current.getAsJsonObject();
                JsonElement existing = object.get("value");
                if (existing == null) throw invalidBoolean(key, path);
                Boolean parsed = readJsonBoolean(existing, key, path);
                if (existing.isJsonPrimitive() && existing.getAsJsonPrimitive().isString()) {
                    object.addProperty("value", Boolean.toString(value));
                } else if (parsed != null) {
                    object.addProperty("value", value);
                }
            } else throw invalidBoolean(key, path);
        }

        byte[] updatedBytes() {
            String text;
            if (lineConfig) text = String.join(lineSeparator, lines);
            else text = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json);
            return text.getBytes(StandardCharsets.UTF_8);
        }

        private static Boolean readJsonBoolean(JsonElement element, String key, Path path) throws IOException {
            if (element.isJsonPrimitive()) {
                var primitive = element.getAsJsonPrimitive();
                if (primitive.isBoolean()) return primitive.getAsBoolean();
                if (primitive.isString()) {
                    Boolean value = parseBoolean(primitive.getAsString());
                    if (value != null) return value;
                }
            } else if (element.isJsonObject()) {
                JsonElement value = element.getAsJsonObject().get("value");
                if (value != null) return readJsonBoolean(value, key, path);
            }
            throw invalidBoolean(key, path);
        }

        private static Boolean readLineBoolean(String[] lines, String key, Path path) throws IOException {
            Boolean result = null;
            for (String line : lines) {
                LineValue value = lineValue(line, key, path);
                if (value == null) continue;
                if (result != null) throw new IOException("Duplicate config key " + key + " in " + path);
                result = value.value();
            }
            return result;
        }

        private static String[] writeLineBoolean(String[] source, String key, boolean value,
                                                 Path path, String missingLineSeparator) throws IOException {
            List<String> updated = new ArrayList<>(List.of(source));
            int found = -1;
            for (int i = 0; i < updated.size(); i++) {
                if (lineValue(updated.get(i), key, path) != null) {
                    if (found >= 0) throw new IOException("Duplicate config key " + key + " in " + path);
                    found = i;
                }
            }
            if (found < 0) {
                String entry = key + missingLineSeparator + value;
                if (updated.size() == 1 && updated.get(0).isEmpty()) updated.set(0, entry);
                else updated.add(entry);
            } else {
                String line = updated.get(found);
                int separator = delimiter(line);
                int valueStart = separator + 1;
                while (valueStart < line.length() && Character.isWhitespace(line.charAt(valueStart))) valueStart++;
                int valueEnd = valueStart;
                while (valueEnd < line.length() && !Character.isWhitespace(line.charAt(valueEnd))
                        && line.charAt(valueEnd) != '#' && line.charAt(valueEnd) != ';') valueEnd++;
                updated.set(found, line.substring(0, valueStart) + value + line.substring(valueEnd));
            }
            return updated.toArray(String[]::new);
        }

        private static LineValue lineValue(String line, String key, Path path) throws IOException {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) return null;
            int separator = delimiter(line);
            if (separator < 0 || !line.substring(0, separator).trim().equals(key)) return null;
            int start = separator + 1;
            while (start < line.length() && Character.isWhitespace(line.charAt(start))) start++;
            int end = start;
            while (end < line.length() && !Character.isWhitespace(line.charAt(end))
                    && line.charAt(end) != '#' && line.charAt(end) != ';') end++;
            Boolean value = parseBoolean(line.substring(start, end));
            if (value == null) throw invalidBoolean(key, path);
            String suffix = line.substring(end).trim();
            if (!suffix.isEmpty() && !suffix.startsWith("#") && !suffix.startsWith(";")) throw invalidBoolean(key, path);
            return new LineValue(value);
        }

        private static int delimiter(String line) {
            int equals = line.indexOf('=');
            int colon = line.indexOf(':');
            if (equals < 0) return colon;
            if (colon < 0) return equals;
            return Math.min(equals, colon);
        }

        private static Boolean parseBoolean(String value) {
            if ("true".equalsIgnoreCase(value)) return true;
            if ("false".equalsIgnoreCase(value)) return false;
            return null;
        }

        private static IOException invalidBoolean(String key, Path path) {
            return new IOException("Invalid boolean config key " + key + " in " + path);
        }

        private record LineValue(boolean value) {}
    }
}
