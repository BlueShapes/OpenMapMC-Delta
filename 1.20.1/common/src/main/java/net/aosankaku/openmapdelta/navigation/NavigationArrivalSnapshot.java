// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.navigation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Copies destination labels before arrival removes their live markers. */
public final class NavigationArrivalSnapshot {
    private NavigationArrivalSnapshot() {}

    public static <K, V, R> List<R> capture(List<K> arrivedIds, Map<K, V> liveValues,
                                            Function<? super V, ? extends R> snapshot) {
        List<R> captured = new ArrayList<>(arrivedIds.size());
        for (K id : arrivedIds) {
            V value = liveValues.get(id);
            if (value == null) continue;
            R copied = snapshot.apply(value);
            if (copied != null) captured.add(copied);
        }
        return List.copyOf(captured);
    }
}
