// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat.mapselection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure startup policy for choosing which installed map provider owns the HUD. */
public final class MapModSelectionModel {
    public static final String ODYSSEY_MAP = "odysseymap";
    public static final String JOURNEY_MAP = "journeymap";
    public static final String XAERO_MINIMAP = "xaerominimap";
    public static final Set<String> PROVIDER_IDS = Set.of(ODYSSEY_MAP, JOURNEY_MAP, XAERO_MINIMAP);

    private MapModSelectionModel() {}

    /**
     * Hashes normalized provider IDs and versions in ID order. Length-prefixed
     * fields avoid ambiguities even if a version contains punctuation.
     */
    public static String fingerprint(Map<String, String> installedVersions) {
        if (installedVersions == null) throw new IllegalArgumentException("installedVersions is required");
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        Set<String> normalizedIds = new HashSet<>();
        for (Map.Entry<String, String> entry : installedVersions.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Provider IDs and versions must not be null");
            }
            String id = entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty() || !normalizedIds.add(id)) {
                throw new IllegalArgumentException("Provider IDs must be unique and non-empty");
            }
            entries.add(Map.entry(id, entry.getValue().trim()));
        }
        entries.sort(Map.Entry.comparingByKey(Comparator.naturalOrder()));
        StringBuilder canonical = new StringBuilder();
        for (Map.Entry<String, String> entry : entries) {
            appendField(canonical, entry.getKey());
            appendField(canonical, entry.getValue());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /**
     * Prompts once for initial co-installation. After the user acknowledges a
     * provider set, any changed set or version prompts again, even for one mod.
     */
    public static boolean requiresPrompt(MapModSelectionState acknowledged,
                                         Map<String, String> installedVersions) {
        String currentFingerprint = fingerprint(installedVersions);
        int providerCount = 0;
        for (String id : installedVersions.keySet()) {
            if (id != null && PROVIDER_IDS.contains(id.trim().toLowerCase(Locale.ROOT))) providerCount++;
        }
        if (acknowledged == null) return providerCount >= 2;
        return !acknowledged.fingerprint().equals(currentFingerprint);
    }

    static void appendField(StringBuilder target, String field) {
        target.append(field.length()).append(':').append(field).append(';');
    }
}
