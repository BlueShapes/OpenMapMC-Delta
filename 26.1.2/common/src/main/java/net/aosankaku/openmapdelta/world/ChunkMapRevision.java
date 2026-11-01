// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

/** Revision attached to a chunk, including complete packet replacements of the same object. */
public interface ChunkMapRevision {
    long odysseymap$getMapRevision();
}
