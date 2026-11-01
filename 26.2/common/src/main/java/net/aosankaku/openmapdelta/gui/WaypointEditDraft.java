// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import java.util.Objects;

/** Exact editor input used to detect edits without normalizing invalid or formatted text. */
public record WaypointEditDraft(String name, String x, String y, String z, String color, String iconId, String category) {
    public WaypointEditDraft {
        Objects.requireNonNull(name);
        Objects.requireNonNull(x);
        Objects.requireNonNull(y);
        Objects.requireNonNull(z);
        Objects.requireNonNull(color);
        Objects.requireNonNull(iconId);
        Objects.requireNonNull(category);
    }

    public WaypointEditDraft(String name, String x, String y, String z, String color, String iconId) {
        this(name, x, y, z, color, iconId, "");
    }

    public record AppearanceChanges(boolean color, boolean icon, boolean category) {}

    /** Preserve freshly synchronized station fields that the user did not edit in this form. */
    public AppearanceChanges appearanceChangesComparedTo(WaypointEditDraft baseline) {
        return new AppearanceChanges(!color.equals(baseline.color), !iconId.equals(baseline.iconId),
                !category.equals(baseline.category));
    }

    public boolean isDirtyComparedTo(WaypointEditDraft baseline) {
        return !equals(baseline);
    }
}
