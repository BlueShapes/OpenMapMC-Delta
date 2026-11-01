// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.util.ArrayList;
import java.util.List;

public final class WaypointClusteringTest {
    private static int assertions;

    public static void main(String[] args) {
        nameCutoffBoundary();
        nearMarkersStayIndividual();
        farMarkersClusterOnIconsOrLongNames();
        groupedAnchorsUseTheScreenCenter();
        projectedAnchorsStayWithinRenderRange();
        individualAnchorsAndBeamColors();
        farNamesRespectAim();
        focusedClusterShowsItsReadableMembers();
        cameraDirectionControlsNearAndClusteredNames();
        highlightedMembersDoNotExposeOtherNames();
        clusterNamesAreCappedAndPackedUsingMeasuredSummary();
        textPackingAvoidsOtherStacksAndIcons();
        cameraOrientationAndBeamOnly();
        zeroCameraDepthIsSafe();
        boundedClustersAndCandidateCounts();
        badgeReadability();
        System.out.println("Waypoint clustering tests passed (" + assertions + " assertions).");
    }

    private static void nameCutoffBoundary() {
        check(WaypointClustering.isWithinNameRange(149.999, 150.0), "just inside the name cutoff is near");
        check(!WaypointClustering.isWithinNameRange(150.0, 150.0), "the exact cutoff is far");
        check(!WaypointClustering.isWithinNameRange(150.001, 150.0), "just outside the cutoff is far");
        check(WaypointClustering.isWithinNameRange(1_000_000, 0.0), "zero disables far grouping");
        check(!WaypointClustering.isWithinNameRange(Double.NaN, 150.0), "invalid distances are not near");
    }

    private static void nearMarkersStayIndividual() {
        var nearA = entry("near A", 0, 0, 100, 160, true, false, true);
        var nearB = entry("near B", 0.1, 0, 100, 180, true, false, true);
        var overlapping = WaypointClustering.layout(List.of(nearA, nearB), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(overlapping.size() == 2 && overlapping.stream().allMatch(stack -> stack.count() == 1),
                "overlapping near waypoints keep their individual icons and beams");
        check(overlapping.stream().allMatch(stack -> stack.namedEntries().isEmpty()),
                "near waypoints do not show names when their gaze policy is false");
        check(overlapping.stream().allMatch(stack -> stack.labelBounds() == null),
                "hidden near names do not reserve text rectangles");

        var disabled = WaypointClustering.layout(List.of(
                entry("zero A", 0, 0, 100, 60, true, false, true),
                entry("zero B", 0, 0, 100, 60, true, false, true)),
                0, 0, 128, 64, 9, 2, WaypointClusteringTest::countWidth);
        check(disabled.size() == 2 && disabled.stream().noneMatch(WaypointClustering.LayoutStack::aggregated),
                "a disabled name range leaves all waypoints individual");
        check(disabled.stream().allMatch(stack -> stack.namedEntries().isEmpty()),
                "disabling distance grouping does not disable the gaze condition");
    }

    private static void farMarkersClusterOnIconsOrLongNames() {
        var shortA = entry("short A", 0, 0, 100, 30, false, false, true);
        var shortB = entry("short B", 40, 0, 100, 30, false, false, true);
        var iconGroups = WaypointClustering.layout(List.of(shortA, shortB), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(iconGroups.size() == 2, "separated short names remain separate far waypoints");

        var longA = entry("long A", 0, 0, 100, 220, false, false, true);
        var longB = entry("long B", 25, 0, 100, 220, false, false, true);
        var textGroups = WaypointClustering.layout(List.of(longA, longB), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(textGroups.size() == 1 && textGroups.get(0).aggregated() && textGroups.get(0).count() == 2,
                "long far names aggregate even when their icon centers do not overlap");

        var mixed = WaypointClustering.layout(List.of(
                entry("near", 0, 0, 100, 100, true, false, true), longA, longB),
                0, 0, 128, 64, 9, 2, WaypointClusteringTest::countWidth);
        check(mixed.size() == 2 && mixed.get(0).near() && mixed.get(0).count() == 1
                        && mixed.get(1).aggregated() && mixed.get(1).count() == 2,
                "near members never enter a far count badge");
    }

    private static void farNamesRespectAim() {
        var farA = entry("far A", 0, 0, 100, 48, false, false, true);
        var farB = entry("far B", 1, 0, 100, 55, false, false, true);
        var aggregate = WaypointClustering.layout(List.of(farA, farB), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        check(aggregate.aggregated() && aggregate.count() == 2 && aggregate.namedEntries().isEmpty(),
                "a far cluster keeps its count but hides names when not aimed");
        check(aggregate.labelBounds() == null, "a cluster with hidden names reserves no label stack");

        var focused = WaypointClustering.layout(List.of(
                entry("focused A", 0, 0, 100, 48, false, true, true),
                entry("focused B", 1, 0, 100, 55, false, true, true)), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        check(focused.namedEntries().size() == 2 && focused.labelBounds() != null,
                "eligible far names still form a vertical stack above the count");

        var aimed = entry("far singleton", 100, 0, 100, 70, false, true, true);
        var hidden = entry("far hidden", 200, 0, 100, 70, false, false, true);
        var singletons = WaypointClustering.layout(List.of(aimed, hidden), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(singletons.get(0).namedEntries().size() == 1,
                "a far singleton preserves its prior aimed label behavior");
        check(singletons.get(1).namedEntries().isEmpty(),
                "a far singleton without an aimed label does not add clutter");
    }

    private static void focusedClusterShowsItsReadableMembers() {
        List<WaypointClustering.Entry<String>> entries = List.of(
                gazeEntry("focused A", -4.0), gazeEntry("focused B", 0.0),
                gazeEntry("focused C", 5.0), gazeEntry("just outside", 6.2));
        long individuallyFocused = entries.stream().filter(WaypointClustering.Entry::labelVisible).count();
        check(individuallyFocused == 3, "the regression fixture has exactly three names inside the six-degree cone");

        var stacks = WaypointClustering.layout(entries, 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(stacks.size() == 1 && stacks.get(0).aggregated() && stacks.get(0).count() == 4,
                "long readable labels still form one four-waypoint cluster");
        check(stacks.get(0).namedEntries().size() == 4,
                "aiming at the group makes every readable ordinary member name eligible");
    }

    private static void groupedAnchorsUseTheScreenCenter() {
        for (double yaw : new double[]{-70, 0, 80}) {
            for (double pitch : new double[]{-50, 0, 45}) {
                var first = screenEntry("first", -0.018, 0.010, 30, yaw, pitch);
                var second = screenEntry("second", 0.030, -0.020, 120, yaw, pitch);
                var groups = WaypointClustering.layout(List.of(first, second), yaw, pitch, 128, 64,
                        9, 2, WaypointClusteringTest::countWidth);
                check(groups.size() == 1 && groups.get(0).count() == 2, "unequal depths still form one group");
                var group = groups.get(0);
                double[] center = screenPoint(group.anchor(), yaw, pitch);
                close(center[0], 0.006, "badge is horizontally midway between both screen positions");
                close(center[1], -0.005, "badge is vertically midway between both screen positions");
                check(group.representative() == first.candidate(), "centering preserves representative priority");
                check(WaypointRenderMath.isInFront(group.anchor().dx(), group.anchor().dy(), group.anchor().dz(),
                        yaw, pitch), "center remains in front of the rotated camera");
                double expectedRadius = (length(first.candidate()) + length(second.candidate())) / 2.0;
                close(length(group.anchor()), expectedRadius, "anchor stays at the mean renderable radius");
                double factor = WaypointRenderMath.billboardScale(
                        WaypointRenderMath.projectedDistance(group.anchor().distance(), 128)) / center[2];
                var bounds = group.labelBounds();
                close((bounds.minX() + bounds.maxX()) / 2.0, center[0], "names share the badge's horizontal center");
                double labelOffset = group.badgeRadius() + 2 + 9 - 4 + 11 / 2.0 + group.verticalOffset();
                close((bounds.minY() + bounds.maxY()) / 2.0, center[1] + labelOffset * factor,
                        "label packing uses the centered anchor and its actual scale");

                var reversed = WaypointClustering.layout(List.of(second, first), yaw, pitch, 128, 64,
                        9, 2, WaypointClusteringTest::countWidth).get(0);
                double[] reversedCenter = screenPoint(reversed.anchor(), yaw, pitch);
                close(reversedCenter[0], center[0], "member priority cannot pull the group sideways");
                close(reversedCenter[1], center[1], "member priority cannot pull the group vertically");
                check(reversed.representative() == second.candidate(), "reordering can still choose another style");

                var third = screenEntry("third", -0.006, 0.016, 90, yaw, pitch);
                var triple = WaypointClustering.layout(List.of(first, second, third), yaw, pitch, 128, 64,
                        9, 2, WaypointClusteringTest::countWidth).get(0);
                check(triple.count() == 3, "all three overlapping members contribute to the center");
                double[] tripleCenter = screenPoint(triple.anchor(), yaw, pitch);
                close(tripleCenter[0], 0.002, "three-member group uses the horizontal centroid");
                close(tripleCenter[1], 0.002, "three-member group uses the vertical centroid");
            }
        }
    }

    private static void projectedAnchorsStayWithinRenderRange() {
        var first = WaypointRenderMath.projectCandidate("close", -1, 0, 30, 128);
        var second = WaypointRenderMath.projectCandidate("distant", 500, 0, 10000, 128);
        var group = WaypointClustering.layout(List.of(
                new WaypointClustering.Entry<>(first, 120, false, false, true),
                new WaypointClustering.Entry<>(second, 120, false, false, true)), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        check(group.count() == 2, "independently clamped members with different depths overlap");
        double[] center = screenPoint(group.anchor(), 0, 0);
        close(center[0], -(first.dx() / first.dz() + second.dx() / second.dz()) / 2.0,
                "clamping does not bias the horizontal midpoint toward the deeper member");
        close(center[1], (first.dy() / first.dz() + second.dy() / second.dz()) / 2.0,
                "icon height and clamping are included in the visible vertical midpoint");
        close(length(group.anchor()), (length(first) + length(second)) / 2.0,
                "synthetic group anchor stays inside the existing member render range");
        close(group.anchor().distance(), (first.distance() + second.distance()) / 2.0,
                "synthetic display distance is averaged independently of the representative");
    }

    private static void individualAnchorsAndBeamColors() {
        var first = entry("first", 0, 0, 100, 48, false, false, true);
        var second = entry("second", 1, 0, 100, 48, false, false, true);
        var group = WaypointClustering.layout(List.of(first, second), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        for (int color : new int[]{0xFF000000, 0xFFFF0000, 0xFF00FF00, 0x800000FF}) {
            check(group.beamColor(color) == 0xFFFFFFFF, "every grouped beam uses fixed white");
        }
        var singleton = WaypointClustering.layout(List.of(first), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        check(singleton.anchor() == first.candidate(), "far singleton retains its exact original anchor");
        check(singleton.beamColor(0x80ABCDEF) == 0x80ABCDEF, "individual beam retains its marker color");
        var near = entry("near", 0, 0, 100, 48, true, false, true);
        var nearStack = WaypointClustering.layout(List.of(near, first), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        check(nearStack.anchor() == near.candidate(), "near marker never moves to a group center");
        var behind = entry("beam only", 0, 0, -10, 40, false, false, false);
        var beamStack = WaypointClustering.layout(List.of(behind), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        check(beamStack.anchor() == behind.candidate(), "beam-only marker keeps its original anchor");
    }

    private static void cameraDirectionControlsNearAndClusteredNames() {
        for (double yaw : new double[]{-30, 0, 30}) {
            for (double pitch : new double[]{-30, 0, 30}) {
                List<WaypointClustering.Entry<String>> entries = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    double x = i % 2, z = i < 2 ? 80 : 1000;
                    boolean looking = WaypointRenderMath.isLookingAt(x, 0, z, yaw, pitch,
                            WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES);
                    entries.add(entry("waypoint " + i, x, 0, z, 90, i < 2, looking, true));
                }
                var stacks = WaypointClustering.layout(entries, yaw, pitch, 128, 64,
                        9, 2, WaypointClusteringTest::countWidth);
                check(stacks.size() == 3 && stacks.stream().mapToInt(WaypointClustering.LayoutStack::count).sum() == 4,
                        "camera movement preserves individual near markers and the far group count");
                int visibleNames = stacks.stream().mapToInt(stack -> stack.namedEntries().size()).sum();
                check(visibleNames == (yaw == 0 && pitch == 0 ? 4 : 0),
                        "turning yaw or pitch away hides both near and grouped waypoint names");
            }
        }
    }

    private static void highlightedMembersDoNotExposeOtherNames() {
        var ordinary = entry("ordinary off gaze", 0, 0, 100, 60, false, false, true);
        var destination = entry("navigation", 1, 0, 100, 60, false, true, true);
        var notice = entry("location notice", 2, 0, 100, 60, false, true, true);
        var group = WaypointClustering.layout(List.of(destination, ordinary, notice), 20, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        check(group.count() == 3 && group.aggregated(), "highlighted members retain the full group count");
        check(group.namedEntries().stream().map(WaypointClustering.Entry::value).toList()
                        .equals(List.of("navigation", "location notice")),
                "always-visible targets do not reveal an ordinary off-gaze name in the same group");

        var faded = new WaypointClustering.Entry<>(destination.candidate(), 60, false, true, true, 15, false);
        var fadedGroup = WaypointClustering.layout(List.of(faded, ordinary), 20, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        check(fadedGroup.count() == 2 && fadedGroup.namedEntries().isEmpty(),
                "highlighting does not bypass proximity fading or another member's gaze condition");

        var fadedFocused = new WaypointClustering.Entry<>(
                new WaypointRenderMath.ViewCandidate<>("faded focused cue", 0, 0, 100, 100),
                20, false, true, true, Double.NaN, false);
        var ordinaryOutside = gazeEntry("ordinary outside gaze", 20.0, 500);
        check(WaypointRenderMath.isLookingAt(fadedFocused.candidate().dx(), fadedFocused.candidate().dy(),
                        fadedFocused.candidate().dz(), 0, 0, WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES)
                        && !WaypointRenderMath.isLookingAt(ordinaryOutside.candidate().dx(),
                        ordinaryOutside.candidate().dy(), ordinaryOutside.candidate().dz(), 0, 0,
                        WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES),
                "the faded-only focus fixture separates the focused cue from an ordinary off-gaze name");
        var fadedOnlyFocus = WaypointClustering.layout(List.of(fadedFocused, ordinaryOutside), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        check(fadedOnlyFocus.count() == 2 && fadedOnlyFocus.aggregated()
                        && fadedOnlyFocus.namedEntries().isEmpty()
                        && fadedOnlyFocus.labelSelection().remaining() == 0,
                "a faded focused member cannot promote an off-gaze ordinary name");
    }

    private static void clusterNamesAreCappedAndPackedUsingMeasuredSummary() {
        List<WaypointClustering.Entry<String>> entries = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            entries.add(gazeEntry("name " + i, i == 4 ? 6.2 : 0.0, i == 4 ? 500 : 20));
        }
        entries.add(new WaypointClustering.Entry<>(
                new WaypointRenderMath.ViewCandidate<>("faded", 0, 0, 100, 20),
                20, false, true, true, Double.NaN, false));

        int[] selectionCalls = {0};
        int[] summaryWidthCalls = {0};
        var cluster = WaypointClustering.layout(entries, 0, 0, 128, 64, 9, 2,
                WaypointClusteringTest::countWidth,
                remaining -> {
                    summaryWidthCalls[0]++;
                    check(remaining == 15, "only eligible names contribute to the remaining count");
                    return 80;
                },
                named -> {
                    selectionCalls[0]++;
                    return WaypointClusterLabels.select(named, item -> item.value().equals("name 19"));
                }).get(0);
        check(selectionCalls[0] == 1, "the label selection callback runs once before packing");
        check(summaryWidthCalls[0] == 1, "the actual summary text width is measured once");
        check(cluster.count() == 21 && cluster.aggregated(), "the full cluster membership and count remain intact");
        check(cluster.namedEntries().size() == 20, "full visible eligible names remain available for visibility");
        check(cluster.labelSelection().names().stream().map(WaypointClustering.Entry::value).toList()
                        .equals(List.of("name 0", "name 1", "name 2", "name 3", "name 19")),
                "a prioritized name joins the first four while display order remains unchanged");
        check(cluster.labelSelection().remaining() == 15,
                "faded entries do not inflate the summary count while off-gaze readable names do");

        double factor = WaypointRenderMath.billboardScale(WaypointRenderMath.projectedDistance(
                cluster.anchor().distance(), 128)) / screenPoint(cluster.anchor(), 0, 0)[2];
        close(cluster.labelBounds().maxX() - cluster.labelBounds().minX(), 84 * factor,
                "summary measured width determines the packed label width");
        close(cluster.labelBounds().maxY() - cluster.labelBounds().minY(), 74 * factor,
                "packing reserves exactly five names plus the summary row");

        var iconAndCluster = new ArrayList<>(entries.subList(0, 20));
        iconAndCluster.add(entry("icon only", 0, 7, 100, 20, true, false, true));
        var placed = WaypointClustering.layout(iconAndCluster, 0, 0, 128, 64, 9, 2,
                WaypointClusteringTest::countWidth,
                ignored -> 80,
                named -> WaypointClusterLabels.select(named, item -> false));
        check(placed.get(0).verticalOffset() > 0.0,
                "the six-row summary stack moves clear of another waypoint's icon");
    }

    private static void textPackingAvoidsOtherStacksAndIcons() {
        var first = entry("first", 0, 0, 100, 70, true, true, true);
        var second = entry("second", 0.1, 0, 100, 70, true, true, true);
        var packed = WaypointClustering.layout(List.of(first, second), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(packed.get(0).labelBounds() != null && packed.get(1).labelBounds() != null,
                "near labels receive camera-space bounds");
        check(!packed.get(0).labelBounds().overlaps(packed.get(1).labelBounds()),
                "overlapping near text rectangles are stacked without overlap");
        check(packed.get(1).verticalOffset() > 0.0,
                "the colliding label moves up while its icon anchor stays fixed");

        var customIcon = new WaypointClustering.Entry<>(
                new WaypointRenderMath.ViewCandidate<>("custom", 0, 0, 100, 100),
                60, true, true, true, 8 + 9 + 2 - 4.0);
        var customPlacement = WaypointClustering.layout(List.of(customIcon), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth).get(0);
        double factor = WaypointRenderMath.billboardScale(100) / 100.0;
        double textCenter = (customPlacement.labelBounds().minY() + customPlacement.labelBounds().maxY()) / 2.0;
        check(Math.abs(textCenter - 15.0 * factor) < 1.0e-9,
                "custom-icon label bounds match its actual y anchor and text-background rectangle");

        var label = entry("name", 0, 0, 100, 100, true, true, true);
        var icon = entry("other icon", 0, 3, 100, 30, true, false, true);
        var avoidsIcon = WaypointClustering.layout(List.of(label, icon), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(avoidsIcon.get(0).verticalOffset() > 0.0,
                "label packing also clears another waypoint's fixed icon bounds");

        var cameraYawA = entry("west A", -100, 0, 0, 70, true, true, true);
        var cameraYawB = entry("west B", -100, 0.1, 0, 70, true, true, true);
        var yawed = WaypointClustering.layout(List.of(cameraYawA, cameraYawB), 90, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(!yawed.get(0).labelBounds().overlaps(yawed.get(1).labelBounds()),
                "label packing follows camera yaw");

        double p = Math.sqrt(0.5) * 100;
        var downA = entry("down A", 0, -p, p, 60, true, true, true);
        var downB = entry("down B", 0.1, -p, p, 60, true, true, true);
        var pitched = WaypointClustering.layout(List.of(downA, downB), 0, 45, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(!pitched.get(0).labelBounds().overlaps(pitched.get(1).labelBounds()),
                "label packing follows camera pitch");
    }

    private static void cameraOrientationAndBeamOnly() {
        double bottom = -20.0, top = 20.0;
        check(!WaypointRenderMath.isInFront(0, -10, 10, 0, -90), "icon center can be behind looking up");
        check(WaypointRenderMath.isBeamInFront(0, 10, bottom, top, 0, -90),
                "upper beam endpoint remains visible looking up");
        check(!WaypointRenderMath.isInFront(0, 10, 10, 0, 90), "icon center can be behind looking down");
        check(WaypointRenderMath.isBeamInFront(0, 10, bottom, top, 0, 90),
                "lower beam endpoint remains visible looking down");
        check(!WaypointRenderMath.isBeamInFront(0, -10, bottom, top, 0, 0), "fully rear beams are culled");

        var behind = entry("beam only", 0, 0, -10, 40, false, true, false);
        var visibleBeam = WaypointClustering.layout(List.of(behind), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(visibleBeam.size() == 1 && visibleBeam.get(0).namedEntries().size() == 1,
                "beam-only candidates stay individual and keep eligible singleton labels");
    }

    private static void boundedClustersAndCandidateCounts() {
        List<WaypointClustering.Entry<String>> candidates = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            candidates.add(entry("separate " + i, i * 100, 0, 100, 6, false, false, true));
        }
        candidates.add(entry("late overlap", 0, 0, 100, 6, false, false, true));
        candidates.add(entry("late separated", 6500, 0, 100, 6, false, false, true));
        var full = WaypointClustering.layout(candidates, 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(full.size() == 64, "the number of rendered icon groups remains bounded");
        check(full.get(0).count() == 2 && full.get(0).aggregated(),
                "candidates beyond the group cap still contribute to an existing count");
        check(full.stream().mapToInt(WaypointClustering.LayoutStack::count).sum() == 65,
                "the late separated candidate is dropped but late overlaps are counted exactly once");

        var bounded = WaypointClustering.layout(List.of(
                entry("chain A", 0, 0, 100, 6, false, false, true),
                entry("chain B", 3, 0, 100, 6, false, false, true),
                entry("chain C", 6, 0, 100, 6, false, false, true)), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(bounded.size() == 2 && bounded.get(0).count() == 2,
                "overlap chains cannot swallow an arbitrarily wide screen area");
    }

    private static void badgeReadability() {
        for (int width : new int[]{6, 12, 18, 24, 60}) {
            float radius = WaypointClusterBadge.radius(width, 9);
            check(Math.hypot(width / 2.0, 4.5) < radius - 1,
                    "all numeral corners fit inside the disk for one to ten digits");
            List<Vertex> vertices = new ArrayList<>();
            WaypointClusterBadge.emit(radius, false, 0xFFFFFFFF,
                    (x, y, color) -> vertices.add(new Vertex(x, y, color)));
            check(vertices.size() == 384, "circle geometry stays bounded in quad mode");
            check(vertices.stream().allMatch(v -> Math.hypot(v.x, v.y) <= radius + 1.0001),
                    "every circle vertex stays inside its outline");
            check(vertices.stream().anyMatch(v -> v.color == 0xFFFFFFFF), "normal ring is white");
            check(vertices.stream().anyMatch(v -> v.color == 0xE6000000), "count gets a dark backdrop");
            for (int i = 0; i < vertices.size(); i += 4) {
                Vertex a = vertices.get(i), b = vertices.get(i + 1), c = vertices.get(i + 2);
                check((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) <= 0,
                        "circle quad winding stays compatible with the text backdrop renderer");
            }
        }
        List<Integer> colors = new ArrayList<>();
        WaypointClusterBadge.emit(11, true, 0x80FFFFFF, (x, y, color) -> colors.add(color));
        check(colors.contains(0x80FFFF55), "emphasized groups get a yellow ring with fade alpha");
        check(colors.contains(0x73000000), "badge disk respects the source waypoint opacity");
    }

    private static void zeroCameraDepthIsSafe() {
        var first = entry("at camera A", 0, 0, 0, 20, false, false, true);
        var second = entry("at camera B", 0, 0, 0, 20, false, false, true);
        var layout = WaypointClustering.layout(List.of(first, second), 0, 0, 128, 64,
                9, 2, WaypointClusteringTest::countWidth);
        check(layout.size() == 2 && layout.stream().allMatch(stack -> stack.count() == 1),
                "zero camera depth stays separate without projection divisions");
    }

    private static WaypointClustering.Entry<String> entry(String name, double x, double y, double z,
                                                            int width, boolean near, boolean labelVisible,
                                                            boolean iconVisible) {
        double distance = Math.hypot(Math.hypot(x, y), z);
        return new WaypointClustering.Entry<>(
                new WaypointRenderMath.ViewCandidate<>(name, x, y, z, distance),
                width, near, labelVisible, iconVisible);
    }

    private static WaypointClustering.Entry<String> gazeEntry(String name, double yawOffsetDegrees) {
        return gazeEntry(name, yawOffsetDegrees, 500);
    }

    private static WaypointClustering.Entry<String> gazeEntry(String name, double yawOffsetDegrees, int textWidth) {
        double angle = Math.toRadians(yawOffsetDegrees);
        double dx = Math.sin(angle) * 100.0;
        double dz = Math.cos(angle) * 100.0;
        boolean focused = WaypointRenderMath.isLookingAt(dx, 0.0, dz, 0.0, 0.0,
                WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES);
        var candidate = new WaypointRenderMath.ViewCandidate<>(name, dx, 0.0, dz, 100.0);
        return new WaypointClustering.Entry<>(candidate, textWidth, false, focused, true);
    }

    private static int countWidth(int count) {
        return Integer.toString(count).length() * 6;
    }

    private static WaypointClustering.Entry<String> screenEntry(String name, double x, double y,
                                                                double depth, double yaw, double pitch) {
        double sinYaw = Math.sin(Math.toRadians(yaw)), cosYaw = Math.cos(Math.toRadians(yaw));
        double sinPitch = Math.sin(Math.toRadians(pitch)), cosPitch = Math.cos(Math.toRadians(pitch));
        double dx = depth * (-x * cosYaw - y * sinYaw * sinPitch - sinYaw * cosPitch);
        double dy = depth * (y * cosPitch - sinPitch);
        double dz = depth * (-x * sinYaw + y * cosYaw * sinPitch + cosYaw * cosPitch);
        return entry(name, dx, dy, dz, 96, false, true, true);
    }

    private static double[] screenPoint(WaypointRenderMath.ViewCandidate<?> candidate, double yaw, double pitch) {
        double sinYaw = Math.sin(Math.toRadians(yaw)), cosYaw = Math.cos(Math.toRadians(yaw));
        double sinPitch = Math.sin(Math.toRadians(pitch)), cosPitch = Math.cos(Math.toRadians(pitch));
        double depth = -candidate.dx() * sinYaw * cosPitch - candidate.dy() * sinPitch
                + candidate.dz() * cosYaw * cosPitch;
        return new double[]{(-candidate.dx() * cosYaw - candidate.dz() * sinYaw) / depth,
                (-candidate.dx() * sinYaw * sinPitch + candidate.dy() * cosPitch
                        + candidate.dz() * cosYaw * sinPitch) / depth, depth};
    }

    private static double length(WaypointRenderMath.ViewCandidate<?> candidate) {
        return Math.hypot(Math.hypot(candidate.dx(), candidate.dy()), candidate.dz());
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1.0e-9, message + ": expected " + expected + ", got " + actual);
    }

    private record Vertex(float x, float y, int color) {}
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
