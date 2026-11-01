// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import java.util.List;
import java.util.Objects;

/** Small identity-aware cache for Create's synchronized graph snapshot. */
public final class TransitRailCreateCache {
    private Object manager;
    private Object networks;
    private Object dimension;
    private int version;
    private boolean valid;
    private List<TransitRail> rails = List.of();

    public boolean matches(Object nextManager, Object nextNetworks, int nextVersion, Object nextDimension) {
        return valid && manager == nextManager && networks == nextNetworks && version == nextVersion
                && Objects.equals(dimension, nextDimension);
    }

    public void update(Object nextManager, Object nextNetworks, int nextVersion, Object nextDimension,
                       List<TransitRail> nextRails) {
        manager = Objects.requireNonNull(nextManager, "manager");
        networks = Objects.requireNonNull(nextNetworks, "networks");
        dimension = Objects.requireNonNull(nextDimension, "dimension");
        version = nextVersion;
        rails = List.copyOf(nextRails);
        valid = true;
    }

    public List<TransitRail> rails() { return rails; }

    public void invalidate() {
        manager = null;
        networks = null;
        dimension = null;
        version = 0;
        valid = false;
        rails = List.of();
    }
}
