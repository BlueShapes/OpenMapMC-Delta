import net.aosankaku.openmapdelta.gui.ControllerNavigationLayout;
import net.aosankaku.openmapdelta.gui.MarkerListWidget;
import net.aosankaku.openmapdelta.gui.WaypointListModel;
import net.aosankaku.openmapdelta.marker.Marker;
import net.aosankaku.openmapdelta.marker.MarkerManager;
import net.aosankaku.openmapdelta.marker.MarkerType;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises controller/scroll lifecycle on the production 26.2 MarkerListWidget. */
public final class MarkerListControllerTest {
    private static final int X = 10, Y = 20, WIDTH = 220, HEIGHT = 48;
    private static int assertions;

    public static void main(String[] args) {
        List<Marker> markers = markers(12);
        MarkerManager.get().setNow(0);
        MarkerManager.get().setMarkers(markers);

        controllerFocusIsNavigationOnlyAndTargetsLabels();
        actualMouseSelectionActivatesAndSynchronizesRow();
        actualWheelAndRefreshKeepVisibleFocusIdentity();
        filteringCollapseAndEmptyResultsStayValid();
        periodicRefreshPreservesOrClampsIdentity();
        tinyViewportHasNoFocusTarget();
        actualDoubleClickStillEditsAfterFocusActivation();

        System.out.println("MarkerList controller lifecycle tests passed (" + assertions + " assertions).");
    }

    private static void controllerFocusIsNavigationOnlyAndTargetsLabels() {
        List<Marker> markers = markers(12);
        AtomicInteger selections = new AtomicInteger();
        MarkerListWidget widget = widget(markers, selections, new AtomicInteger());

        widget.setControllerFocused(true);
        check(widget.isControllerFocused(), "right-list focus activates controller-only focus");
        check(widget.getSelected() == null && selections.get() == 0, "focusing the list does not select a marker");
        check(widget.getControllerRowIdentity().markerId() == null,
                "initial controller row is the category header, not a marker selection");
        checkHeaderLabelTarget(widget.getControllerFocusPoint(), "header focus targets its label/collapse action");

        widget.moveControllerSelection(1);
        check(widget.getControllerRowIdentity().markerId().equals(markers.get(0).getId().toString()),
                "moving down reaches the first actual marker row");
        check(widget.getSelected() == null && selections.get() == 0, "controller navigation never calls onSelect");
        checkMarkerLabelTarget(widget.getControllerFocusPoint(), "marker focus targets label, not star/eye/delete");
        check(widget.getControllerFocusPoint().y() == Y + 24,
                "focused marker row stays fully visible at its real row center");

        widget.moveControllerSelection(1);
        widget.moveControllerSelection(-1);
        check(widget.getControllerRowIdentity().markerId().equals(markers.get(0).getId().toString()),
                "up/down navigation returns to the expected actual visible row");
        checkMarkerLabelTarget(widget.getControllerFocusPoint(), "up/down target remains the marker label");
    }

    private static void actualMouseSelectionActivatesAndSynchronizesRow() {
        List<Marker> markers = markers(12);
        AtomicInteger selections = new AtomicInteger();
        MarkerListWidget widget = widget(markers, selections, new AtomicInteger());

        clickMarker(widget, 1);
        check(widget.isControllerFocused(), "a native row click activates controller row focus even if list opened by mouse");
        check(widget.getSelected() == markers.get(1) && selections.get() == 1,
                "actual mouse selection retains the normal selection callback");
        check(widget.getControllerRowIdentity().markerId().equals(markers.get(1).getId().toString()),
                "actual mouse selection synchronizes the controller row identity");

        widget.moveControllerSelection(1);
        check(widget.getControllerRowIdentity().markerId().equals(markers.get(2).getId().toString()),
                "controller movement after mouse selection continues from the clicked row");
        checkMarkerLabelTarget(widget.getControllerFocusPoint(), "post-click controller focus still targets label");
    }

    private static void actualWheelAndRefreshKeepVisibleFocusIdentity() {
        List<Marker> markers = markers(12);
        MarkerListWidget widget = widget(markers, new AtomicInteger(), new AtomicInteger());
        widget.setControllerFocused(true);
        check(widget.mouseScrolled(X + 100, Y + 24, 0, -1), "actual mouse-wheel event is handled");
        check(widget.mouseScrolled(X + 100, Y + 24, 0, -1), "second actual mouse-wheel event is handled");
        String afterWheel = widget.getControllerRowIdentity().markerId();
        check(afterWheel.equals(markers.get(1).getId().toString()),
                "wheel scrolling rebases controller focus to the newly visible page");
        checkMarkerLabelTarget(widget.getControllerFocusPoint(), "wheel-rebased controller row is fully visible");

        widget.refresh();
        check(widget.getControllerRowIdentity().markerId().equals(afterWheel),
                "refresh preserves the scrolled controller row identity");
        checkMarkerLabelTarget(widget.getControllerFocusPoint(), "refresh does not snap the selected target off page");
        widget.setFilter("");
        check(widget.getControllerRowIdentity().markerId().equals(afterWheel),
                "reapplying the same filter preserves the scrolled controller identity");
        checkMarkerLabelTarget(widget.getControllerFocusPoint(), "filter rebuild keeps a fully visible focus target");

        widget.scrollRows(ControllerNavigationLayout.visibleRows(widget.getHeight()));
        String afterPage = widget.getControllerRowIdentity().markerId();
        check(afterPage.equals(markers.get(2).getId().toString()),
                "actual page-scroll operation rebases controller focus to the new page");
        checkMarkerLabelTarget(widget.getControllerFocusPoint(), "page-scroll focus target is fully visible");
        widget.refresh();
        check(widget.getControllerRowIdentity().markerId().equals(afterPage),
                "refresh preserves controller identity after page scrolling");
        widget.setFilter("");
        check(widget.getControllerRowIdentity().markerId().equals(afterPage),
                "filter rebuild preserves controller identity after page scrolling");
        checkMarkerLabelTarget(widget.getControllerFocusPoint(), "page-scrolled filter rebuild remains visible");
    }

    private static void filteringCollapseAndEmptyResultsStayValid() {
        List<Marker> markers = markers(12);
        MarkerListWidget widget = widget(markers, new AtomicInteger(), new AtomicInteger());
        widget.setControllerFocused(true);
        widget.moveControllerSelection(2);
        widget.setFilter("Marker 09");
        check(widget.isControllerFocused(), "filtering does not release controller row focus");
        check(widget.getControllerRowIdentity().markerId() == null
                        && "Places".equals(widget.getControllerRowIdentity().category().name()),
                "filtering out the focused row preserves its still-valid category header identity");
        checkHeaderLabelTarget(widget.getControllerFocusPoint(), "filtered category header remains fully visible");

        widget.moveControllerSelection(1);
        check(widget.getControllerRowIdentity().markerId().equals(markers.get(9).getId().toString()),
                "controller navigation reaches the remaining filtered marker");
        checkMarkerLabelTarget(widget.getControllerFocusPoint(), "filtered focus target is fully visible");

        WaypointListModel.Category category = new WaypointListModel.Category("Places", false);
        widget.setCollapsedCategories(Set.of(category));
        check(widget.getControllerRowIdentity().markerId() == null
                        && category.equals(widget.getControllerRowIdentity().category()),
                "collapsing the focused category moves identity to its visible header");
        checkHeaderLabelTarget(widget.getControllerFocusPoint(), "collapsed category targets the header label action");

        widget.setFilter("no matching waypoint");
        check(widget.getControllerRowIdentity() == null && widget.getControllerFocusPoint() == null,
                "empty results expose no stale controller row or target");
        widget.setFilter("");
        check(widget.isControllerFocused() && widget.getControllerRowIdentity() != null,
                "restoring results clamps controller focus to a valid row");
        check(widget.getControllerFocusPoint() != null, "restored results provide a fully visible target");

        widget.setCollapsedCategories(Set.of());
        check(widget.getControllerRowIdentity() != null && widget.getControllerFocusPoint() != null,
                "expanding the category preserves a valid focus identity and visible target");
    }

    private static void periodicRefreshPreservesOrClampsIdentity() {
        List<Marker> markers = markers(12);
        MarkerListWidget widget = widget(markers, new AtomicInteger(), new AtomicInteger());
        widget.setControllerFocused(true);
        widget.moveControllerSelection(1);
        String identity = widget.getControllerRowIdentity().markerId();

        MarkerManager.get().advance(1001);
        widget.refreshIfNeeded();
        check(widget.getControllerRowIdentity().markerId().equals(identity),
                "periodic refresh preserves an existing marker identity");
        checkMarkerLabelTarget(widget.getControllerFocusPoint(), "periodic refresh keeps the row fully visible");

        Marker removed = markers.get(0);
        MarkerManager.get().setMarkers(markers.subList(1, markers.size()));
        MarkerManager.get().advance(1001);
        widget.refreshIfNeeded();
        check(widget.getControllerRowIdentity() != null,
                "periodic refresh clamps focus after its marker disappears");
        check(!removed.getId().toString().equals(widget.getControllerRowIdentity().markerId()),
                "periodic refresh does not retain an identity for a removed marker");
        check(widget.getControllerFocusPoint() != null,
                "the clamped periodic-refresh row remains fully visible");
    }

    private static void tinyViewportHasNoFocusTarget() {
        MarkerManager.get().setMarkers(markers(2));
        MarkerListWidget widget = new MarkerListWidget(X, Y, WIDTH, 15, ignored -> { }, ignored -> { }, ignored -> { });
        widget.setControllerFocused(true);
        check(widget.getControllerFocusPoint() == null,
                "viewport shorter than one full row has no controller focus target");
    }

    private static void actualDoubleClickStillEditsAfterFocusActivation() {
        List<Marker> markers = markers(3);
        AtomicInteger selections = new AtomicInteger();
        AtomicInteger edits = new AtomicInteger();
        MarkerListWidget widget = widget(markers, selections, edits);
        clickMarker(widget, 0);
        check(widget.isControllerFocused(), "first real marker click activates row focus");
        clickMarker(widget, 0);
        check(selections.get() == 2, "both native clicks still invoke normal selection");
        check(edits.get() == 1, "same-row native double-click still invokes the edit callback once");
    }

    private static MarkerListWidget widget(List<Marker> markers, AtomicInteger selections, AtomicInteger edits) {
        MarkerManager.get().setMarkers(markers);
        return new MarkerListWidget(X, Y, WIDTH, HEIGHT,
                ignored -> selections.incrementAndGet(), ignored -> edits.incrementAndGet(), ignored -> { });
    }

    private static List<Marker> markers(int count) {
        List<Marker> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Marker marker = new Marker(new UUID(0, i + 1), MarkerType.WAYPOINT, String.format("Marker %02d", i));
            marker.setCategory("Places");
            result.add(marker);
        }
        return result;
    }

    private static void clickMarker(MarkerListWidget widget, int markerIndex) {
        double x = widget.getX() + WaypointListModel.targetXs(widget.getWidth(), false, false).get(2);
        double y = widget.getY() + (markerIndex + 1) * WaypointListModel.ROW_HEIGHT + WaypointListModel.ROW_HEIGHT / 2.0;
        check(widget.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonEvent.ButtonInfo(0)), false),
                "native row mouse click is handled");
    }

    private static void checkMarkerLabelTarget(ControllerNavigationLayout.Point point, String message) {
        check(point != null, message + ": focus point exists");
        check(WaypointListModel.actionAt(point.x() - X, WIDTH, 0, false, false) == WaypointListModel.Action.SELECT,
                message + ": target is in the label area");
        check(point.y() >= Y + WaypointListModel.ROW_HEIGHT / 2.0
                        && point.y() <= Y + HEIGHT - WaypointListModel.ROW_HEIGHT / 2.0,
                message + ": target center belongs to a fully visible row");
    }

    private static void checkHeaderLabelTarget(ControllerNavigationLayout.Point point, String message) {
        check(point != null, message + ": focus point exists");
        check(WaypointListModel.actionAt(point.x() - X, WIDTH, 0, true, false)
                        == WaypointListModel.Action.TOGGLE_CATEGORY,
                message + ": target avoids the header visibility eye");
        check(point.y() == Y + WaypointListModel.ROW_HEIGHT / 2.0,
                message + ": target is the visible header row center");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
