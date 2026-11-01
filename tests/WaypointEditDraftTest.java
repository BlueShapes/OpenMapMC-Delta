// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
import net.aosankaku.openmapdelta.gui.WaypointEditDraft;
import net.aosankaku.openmapdelta.navigation.NavigationArrivalSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WaypointEditDraftTest {
    private static int assertions;

    public static void main(String[] args) {
        draftComparisonPreservesExactInput();
        stationFormPreservesUneditedSynchronizedFields();
        arrivalLabelsAreCapturedBeforeRemoval();
        System.out.println("Waypoint edit draft tests passed (" + assertions + " assertions).");
    }

    private static void draftComparisonPreservesExactInput() {
        WaypointEditDraft baseline = new WaypointEditDraft("Station", "10", "64", "-3", "#AABBCC", "item:rail");
        check(!baseline.isDirtyComparedTo(baseline), "an untouched draft is clean");
        check(baseline.isDirtyComparedTo(new WaypointEditDraft("Station ", "10", "64", "-3", "#AABBCC", "item:rail")),
                "raw name whitespace is a change");
        check(baseline.isDirtyComparedTo(new WaypointEditDraft("Station", "+10", "64", "-3", "#AABBCC", "item:rail")),
                "equivalent but differently formatted coordinates are a change");
        check(baseline.isDirtyComparedTo(new WaypointEditDraft("Station", "not-an-int", "64", "-3", "#AABBCC", "item:rail")),
                "invalid coordinate text is still tracked as a change");
        check(baseline.isDirtyComparedTo(new WaypointEditDraft("Station", "10", "64", "-3", "#12GGZZ", "item:rail")),
                "invalid color text is still tracked as a change");
        check(baseline.isDirtyComparedTo(new WaypointEditDraft("Station", "10", "64", "-3", "#AABBCC", "emoji:train")),
                "selected icon is part of the draft");
        check(baseline.isDirtyComparedTo(new WaypointEditDraft("Station", "10", "64", "-3", "#AABBCC", "item:rail", "Travel")),
                "category changes are included in discard confirmation");
        check(new WaypointEditDraft("Station", "10", "64", "-3", "#AABBCC", "item:rail", "Travel ").isDirtyComparedTo(
                new WaypointEditDraft("Station", "10", "64", "-3", "#AABBCC", "item:rail", "Travel")), "raw category input is preserved");
        WaypointEditDraft current = new WaypointEditDraft("Station", "not-an-int", "64", "-3", "#12GGZZ", "item:rail");
        check(current.isDirtyComparedTo(baseline), "invalid raw input makes the draft dirty");
        current = new WaypointEditDraft("Station", "10", "64", "-3", "#AABBCC", "item:rail");
        check(!current.isDirtyComparedTo(baseline), "restoring every field to its baseline clears dirty state");
    }

    private static void stationFormPreservesUneditedSynchronizedFields() {
        WaypointEditDraft opened = new WaypointEditDraft("Station", "10", "64", "-3", "#55CFFF", "symbol:odysseymap:mtr_station", "");
        WaypointEditDraft categoryOnly = new WaypointEditDraft("Station", "10", "64", "-3", "#55CFFF", "symbol:odysseymap:mtr_station", "Work");
        var changes = categoryOnly.appearanceChangesComparedTo(opened);
        check(changes.category() && !changes.color() && !changes.icon(),
                "a category-only save preserves station color and icon loaded after this form opened");
        String loadedColor = "#123456", loadedIcon = "item:rail";
        check((changes.color() ? categoryOnly.color() : loadedColor).equals(loadedColor), "unedited color comes from fresh station state");
        check((changes.icon() ? categoryOnly.iconId() : loadedIcon).equals(loadedIcon), "unedited icon comes from fresh station state");
        WaypointEditDraft colorOnly = new WaypointEditDraft("Station", "10", "64", "-3", "#AABBCC", "symbol:odysseymap:mtr_station", "");
        var colorChanges = colorOnly.appearanceChangesComparedTo(opened);
        check(colorChanges.color() && !colorChanges.icon() && !colorChanges.category(), "color edits preserve the freshly loaded category");
        WaypointEditDraft cleared = new WaypointEditDraft("Station", "10", "64", "-3", "#55CFFF", "", "");
        check(cleared.appearanceChangesComparedTo(opened).icon(), "explicitly clearing the station icon remains an edit");
    }

    private static void arrivalLabelsAreCapturedBeforeRemoval() {
        UUID first = new UUID(0, 1), second = new UUID(0, 2), missing = new UUID(0, 3);
        MutableLabel firstLabel = new MutableLabel("North Station");
        Map<UUID, MutableLabel> live = new LinkedHashMap<>();
        live.put(first, firstLabel);
        live.put(second, new MutableLabel("River Stop"));

        List<String> captured = NavigationArrivalSnapshot.capture(List.of(first, missing, second), live, value -> value.text);
        live.clear();
        firstLabel.text = "Renamed after arrival";
        check(captured.equals(List.of("North Station", "River Stop")),
                "arrival notices retain ordered label values after live markers are removed or changed");
        try {
            captured.add("mutable");
            throw new AssertionError("Arrival snapshot should be immutable");
        } catch (UnsupportedOperationException expected) {
            check(true, "arrival snapshot is immutable");
        }
    }

    private static final class MutableLabel {
        private String text;
        private MutableLabel(String text) { this.text = text; }
    }

    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }
}
