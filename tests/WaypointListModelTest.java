// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
import net.aosankaku.openmapdelta.gui.WaypointListModel;
import net.aosankaku.openmapdelta.gui.WaypointListModel.Category;
import net.aosankaku.openmapdelta.gui.WaypointListModel.Action;
import java.util.Comparator;
import java.util.List;

public final class WaypointListModelTest {
    private static int assertions;
    private record Point(String name, String category, boolean station, boolean visible) {}
    private static Category category(Point p) { return new Category(p.category(), p.station()); }
    public static void main(String[] args) {
        Point shown = new Point("Home", " Places ", false, true);
        Point hidden = new Point("Hidden", "Places", false, false);
        Point station = new Point("Central", "", true, false);
        Point manual = new Point("Camp", "", false, true);
        Point assignedStation = new Point("Depot", "Places", true, true);
        List<Point> points = List.of(shown, hidden, station, manual, assignedStation);
        var rows = WaypointListModel.rows(points, WaypointListModelTest::category, Comparator.comparing(Point::name), p -> true);
        check(rows.stream().filter(r -> !r.isHeader()).count() == 5, "hidden markers and hidden stations stay listed");
        check(rows.stream().filter(r -> r.isHeader()).count() == 3, "uncategorized and automatic stations have distinct default categories");
        var members = WaypointListModel.members(points, WaypointListModelTest::category, new Category("Places", false));
        check(members.equals(List.of(shown, hidden, assignedStation)), "assigned stations and ordinary waypoints share a named category");
        var search = WaypointListModel.rows(points, WaypointListModelTest::category, Comparator.comparing(Point::name), p -> p.name().equals("Home"));
        check(search.size() == 2 && search.get(0).isHeader(), "search retains the matching category header");
        check(WaypointListModel.members(points, WaypointListModelTest::category, search.get(0).category()).size() == 3, "category bulk actions include members excluded by search");
        var order = Comparator.comparing(Point::name);
        Category places = new Category("Places", false);
        Category stations = new Category("", true);
        Category uncategorized = new Category("", false);
        var collapsedPlaces = WaypointListModel.rows(points, WaypointListModelTest::category, order, p -> true, java.util.Set.of(places));
        check(collapsedPlaces.stream().filter(r -> r.isHeader()).count() == 3, "collapsed category keeps every category header");
        check(collapsedPlaces.stream().noneMatch(r -> places.equals(r.category()) && !r.isHeader()), "collapsed category hides all its members");
        check(collapsedPlaces.stream().anyMatch(r -> r.marker() == station), "collapsing one group leaves other groups expanded");
        var collapsedStations = WaypointListModel.rows(points, WaypointListModelTest::category, order, p -> true, java.util.Set.of(stations));
        check(collapsedStations.stream().anyMatch(r -> r.category().equals(stations) && r.isHeader())
                && collapsedStations.stream().noneMatch(r -> r.marker() == station), "automatic station category collapses independently");
        var collapsedUncategorized = WaypointListModel.rows(points, WaypointListModelTest::category, order, p -> true, java.util.Set.of(uncategorized));
        check(collapsedUncategorized.stream().anyMatch(r -> r.category().equals(uncategorized) && r.isHeader())
                && collapsedUncategorized.stream().noneMatch(r -> r.marker() == manual), "uncategorized waypoints collapse separately from stations");
        check(collapsedPlaces.stream().noneMatch(r -> r.marker() == assignedStation), "assigned station follows its named group when collapsed");
        var expandedPlaces = WaypointListModel.rows(points, WaypointListModelTest::category, order, p -> true, java.util.Set.of());
        check(expandedPlaces.stream().filter(r -> places.equals(r.category()) && !r.isHeader()).map(r -> r.marker().name()).toList()
                .equals(List.of("Depot", "Hidden", "Home")), "expanding restores sorted visible, invisible, and assigned station members");
        var filteredCollapsed = WaypointListModel.rows(points, WaypointListModelTest::category, order,
                p -> p.name().equals("Home"), java.util.Set.of(places));
        check(filteredCollapsed.size() == 1 && filteredCollapsed.get(0).isHeader(), "search retains a collapsed matching category header");
        check(WaypointListModel.members(points, WaypointListModelTest::category, places).size() == 3,
                "category bulk actions remain independent of collapse");
        check(WaypointListModel.actionAt(14, 260, 0, false, true) == Action.VISIBILITY, "eye does not delete or select");
        check(WaypointListModel.actionAt(27.9, 260, 0, false, true) == Action.VISIBILITY, "entire drawn eye is clickable");
        check(WaypointListModel.actionAt(28, 260, 0, false, true) == Action.SELECT, "icon and label select");
        check(WaypointListModel.actionAt(244, 260, 0, false, true) == Action.DELETE, "rightmost control deletes manual waypoints");
        check(WaypointListModel.actionAt(244, 260, 0, false, false) == Action.NONE, "station deletion is disabled");
        for (int x : new int[]{2,14,28,100,244}) check(WaypointListModel.actionAt(x, 260, 1, false, false) == Action.EDIT, "right click edits everywhere on a station row");
        check(WaypointListModel.actionAt(15.9, 260, 0, true, false) == Action.TOGGLE_CATEGORY, "header before eye toggles collapse");
        check(WaypointListModel.actionAt(16, 260, 0, true, false) == Action.VISIBILITY, "header eye lower boundary toggles visibility");
        check(WaypointListModel.actionAt(29.9, 260, 0, true, false) == Action.VISIBILITY, "header eye upper interior toggles visibility");
        check(WaypointListModel.actionAt(30, 260, 0, true, false) == Action.TOGGLE_CATEGORY, "header after eye toggles collapse");
        check(WaypointListModel.actionAt(2, 260, 1, true, false) == Action.NONE, "right click on category header does nothing");
        check(WaypointListModel.actionAt(260, 260, 0, false, true) == Action.NONE, "outside edge cannot delete");
        check(WaypointListModel.actionAt(100, 260, 2, false, true) == Action.NONE, "unrelated mouse buttons do nothing");
        check(WaypointListModel.sidebarWidth(854) == 260, "normal screen uses a wider list");
        check(WaypointListModel.sidebarWidth(320) == 160, "small screen retains map space");
        for (int width : new int[]{160,240,320,427,640,854}) check(width - WaypointListModel.sidebarWidth(width) - 8 >= 64, "sidebar preserves minimum map width");
        check(WaypointListModel.targetXs(260, false, true).size() == 4, "controller can snap to manual delete button");
        check(WaypointListModel.targetXs(260, false, false).size() == 3, "controller skips disabled station delete button");
        check(WaypointListModel.targetXs(260, true, false).size() == 2, "category header has separate controller targets for eye and collapse");
        check(WaypointListModel.actionAt(WaypointListModel.targetXs(260, true, false).get(0), 260, 0, true, false) == Action.VISIBILITY,
                "first category controller target is the eye");
        check(WaypointListModel.actionAt(WaypointListModel.targetXs(260, true, false).get(1), 260, 0, true, false) == Action.TOGGLE_CATEGORY,
                "second category controller target toggles collapse");
        for (double x : WaypointListModel.targetXs(260, false, true)) check(WaypointListModel.actionAt(x, 260, 0, false, true) != Action.NONE, "controller targets are clickable");
        System.out.println("Waypoint list tests passed (" + assertions + " assertions).");
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
