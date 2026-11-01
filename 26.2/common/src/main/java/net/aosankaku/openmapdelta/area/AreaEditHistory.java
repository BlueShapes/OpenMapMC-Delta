// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.area;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded immutable-document undo/redo history; callers apply one logical gesture at a time. */
public final class AreaEditHistory {
    public static final int MAX_ENTRIES = 64;
    private final ArrayList<AreaDocument> states = new ArrayList<>();
    private int cursor;

    public AreaEditHistory(AreaDocument initial) {
        states.add(Objects.requireNonNull(initial, "initial"));
    }

    public AreaDocument current() { return states.get(cursor); }
    public boolean canUndo() { return cursor > 0; }
    public boolean canRedo() { return cursor + 1 < states.size(); }

    public void apply(AreaDocument next) {
        Objects.requireNonNull(next, "next");
        if (current().equals(next)) return;
        while (states.size() > cursor + 1) states.remove(states.size() - 1);
        states.add(next);
        cursor++;
        if (states.size() > MAX_ENTRIES + 1) {
            states.remove(0);
            cursor--;
        }
    }

    public AreaDocument undo() {
        if (canUndo()) cursor--;
        return current();
    }

    public AreaDocument redo() {
        if (canRedo()) cursor++;
        return current();
    }

    /** Begins another map edit from the last saved baseline and discards this draft's history. */
    public void reset(AreaDocument initial) {
        states.clear();
        states.add(Objects.requireNonNull(initial, "initial"));
        cursor = 0;
    }

    public List<AreaDocument> snapshots() { return List.copyOf(states); }
}
