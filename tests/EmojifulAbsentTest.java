// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

/** Run without any Emojiful fixture classes on the runtime classpath. */
public final class EmojifulAbsentTest {
    public static void main(String[] args) {
        if (!EmojifulIconRegistry.forEmojiful(() -> false, System::currentTimeMillis).snapshot().isEmpty())
            throw new AssertionError("Absent mod must have no emoji entries");
        if (!EmojifulIconRegistry.forEmojiful(() -> true, System::currentTimeMillis).snapshot().isEmpty())
            throw new AssertionError("Missing optional API must be safe even if platform detection disagrees");
        System.out.println("Emojiful absent-classpath tests passed.");
    }
}
