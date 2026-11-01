// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.render;

import java.util.ArrayList;
import java.util.List;

/** Standalone boundary tests; compile this with any version's WaypointRenderMath.java. */
public final class WaypointRenderMathTest {
    private static int assertions;

    public static void main(String[] args) {
        check(WaypointRenderMath.projectionDistance(0) == 24, "minimum projection distance");
        check(WaypointRenderMath.projectionDistance(2) == 24, "short render distance margin");
        check(WaypointRenderMath.projectionDistance(10) == 120, "render distance projection");
        check(WaypointRenderMath.projectionDistance(32) == 128, "maximum projection distance");
        check(WaypointRenderMath.projectedDistance(2000, 120) == 120, "far projection clamp");
        check(WaypointRenderMath.projectedDistance(40, 120) == 40, "near point remains in place");
        check(WaypointRenderMath.projectedDistance(Double.NaN, 120) == 0, "invalid distance is safe");
        check(WaypointRenderMath.projectionScale(2000, 120) == 0.06, "far vector is direction-preserving");
        check(WaypointRenderMath.projectionScale(40, 120) == 1, "near vector is unchanged");
        check(WaypointRenderMath.ICON_HEIGHT == 1.25, "waypoint icon center height");

        notifyBeaconPulse();
        proximityFade();
        independentLabelFade();
        alphaScaling();

        projectedIconCenter();

        check(WaypointRenderMath.isLookingAt(0, 0, 100, 0, 0, 6), "camera-forward target");
        double atSix = Math.toRadians(6.0);
        check(WaypointRenderMath.isLookingAt(Math.sin(atSix), 0, Math.cos(atSix), 0, 0, 6.0),
                "look angle boundary is included");
        double beyondSix = Math.toRadians(6.01);
        check(!WaypointRenderMath.isLookingAt(Math.sin(beyondSix), 0, Math.cos(beyondSix), 0, 0, 6.0),
                "look angle outside boundary is excluded");
        check(WaypointRenderMath.isLookingAt(0, 1, 0, 0, -90, 0), "vertical view direction");
        beamVisibility();
        beamOnlyCandidatesFillAfterVisibleCenters();
        candidateSelection();

        check(WaypointRenderMath.billboardScale(0) == WaypointRenderMath.MIN_BILLBOARD_SCALE,
                "billboard minimum size");
        check(WaypointRenderMath.billboardScale(100) == 0.16, "distance-proportional billboard size");
        check(WaypointRenderMath.billboardScale(1000) == WaypointRenderMath.MAX_BILLBOARD_SCALE,
                "billboard maximum size");
        check(WaypointRenderMath.beamStartOffset(-64, 80) == -208, "beam starts below Nether floor");
        check(WaypointRenderMath.beamHeight(-64, 320) == 448, "beam reaches beyond world limits");
        check(WaypointRenderMath.beamStartOffset(0, 64) == -128, "beam starts below Overworld floor");
        check(WaypointRenderMath.beamHeight(0, 384) == 448, "beam spans tall dimension");

        System.out.println("Waypoint render math tests passed (" + assertions + " assertions).");
    }


    private static void candidateSelection() {
        List<WaypointRenderMath.ViewCandidate<String>> candidates = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            candidates.add(new WaypointRenderMath.ViewCandidate<>("behind-" + i, 0, 0, -i, i));
        }
        for (int i = 1; i <= 70; i++) {
            double distance = i;
            double angle = Math.toRadians(30.0);
            candidates.add(new WaypointRenderMath.ViewCandidate<>("near-off-axis-" + i,
                    Math.sin(angle) * distance, 0, Math.cos(angle) * distance, distance));
        }
        candidates.add(new WaypointRenderMath.ViewCandidate<>("aimed-distant", 0, 0, 10000, 10000));
        double twoDegrees = Math.toRadians(2.0);
        candidates.add(new WaypointRenderMath.ViewCandidate<>("aimed-two-degrees",
                Math.sin(twoDegrees) * 5000, 0, Math.cos(twoDegrees) * 5000, 5000));
        double sixDegrees = Math.toRadians(6.0);
        candidates.add(new WaypointRenderMath.ViewCandidate<>("aimed-six-degrees",
                Math.sin(sixDegrees) * 3000, 0, Math.cos(sixDegrees) * 3000, 3000));

        List<WaypointRenderMath.ViewCandidate<String>> selected =
                WaypointRenderMath.prioritizeCandidates(candidates, 0, 0, 64);
        check(selected.size() == 64, "candidate render cap");
        check(selected.get(0).value().equals("aimed-distant"),
                "distant target at the crosshair outranks many nearer candidates");
        check(selected.get(1).value().equals("aimed-two-degrees"),
                "focused targets sort by angular distance");
        check(selected.get(2).value().equals("aimed-six-degrees"),
                "six-degree focused boundary is prioritized");
        check(selected.stream().noneMatch(candidate -> candidate.value().startsWith("behind-")),
                "rear-facing targets do not consume render slots");
        check(selected.stream().anyMatch(candidate -> candidate.value().equals("near-off-axis-1")),
                "near front-facing candidates fill remaining slots");
        check(selected.stream().noneMatch(candidate -> candidate.value().equals("near-off-axis-70")),
                "unfocused candidates remain distance-sorted under the cap");
    }

    private static void beamVisibility() {
        double bottom = -20.0;
        double top = 20.0;
        check(!WaypointRenderMath.isInFront(0, -10, 10, 0, -90),
                "icon center can be behind while looking up");
        check(WaypointRenderMath.isBeamInFront(0, 10, bottom, top, 0, -90),
                "upper beam endpoint remains visible while looking up");
        check(!WaypointRenderMath.isInFront(0, 10, 10, 0, 90),
                "icon center can be behind while looking down");
        check(WaypointRenderMath.isBeamInFront(0, 10, bottom, top, 0, 90),
                "lower beam endpoint remains visible while looking down");
        check(!WaypointRenderMath.isBeamInFront(0, -10, bottom, top, 0, 0),
                "a beam wholly behind the camera is culled");

        List<WaypointRenderMath.ViewCandidate<String>> candidates = List.of(
                new WaypointRenderMath.ViewCandidate<>("center-behind-beam-visible", 0, -10, 10, 10));
        List<WaypointRenderMath.ViewCandidate<String>> selected = WaypointRenderMath.prioritizeBeamCandidates(
                candidates, 0, -90, 10, bottom, top);
        check(selected.size() == 1
                        && selected.get(0).value().equals("center-behind-beam-visible"),
                "selection retains targets whose beam is visible while their icon center is behind");
        List<WaypointRenderMath.ViewCandidate<String>> behind = List.of(
                new WaypointRenderMath.ViewCandidate<>("fully-behind", 0, 0, -10, 10));
        check(WaypointRenderMath.prioritizeBeamCandidates(behind, 0, 0, 10, bottom, top).isEmpty(),
                "beam selection excludes a target whose full beam is behind the camera");
    }

    private static void beamOnlyCandidatesFillAfterVisibleCenters() {
        List<WaypointRenderMath.ViewCandidate<String>> candidates = new ArrayList<>();
        for (int i = 1; i <= 64; i++) {
            candidates.add(new WaypointRenderMath.ViewCandidate<>("beam-only-" + i, 0, 0, -10, 10));
        }
        candidates.add(new WaypointRenderMath.ViewCandidate<>("visible-center", 10, 0, 20,
                Math.sqrt(500.0)));

        check(!WaypointRenderMath.isInFront(0, 0, -10, 0, 10)
                        && WaypointRenderMath.isBeamInFront(0, -10, -192, 256, 0, 10),
                "near rear-facing centers can qualify through their downward beam endpoint");
        List<WaypointRenderMath.ViewCandidate<String>> selected = WaypointRenderMath.prioritizeBeamCandidates(
                candidates, 0, 10, 64, -192, 256);
        check(selected.size() == 64, "beam-only candidates still fill unused render slots");
        check(selected.get(0).value().equals("visible-center"),
                "a visible waypoint center stays ahead of nearer beam-only fallback candidates");
        check(selected.stream().filter(candidate -> candidate.value().startsWith("beam-only-")).count() == 63,
                "beam-only candidates consume slots only after center-visible candidates");
    }

    private static void notifyBeaconPulse() {
        check(WaypointRenderMath.notifyBeaconPulse(0, 0.0F) == 1.0F,
                "notify pulse starts at full brightness");
        checkClose(WaypointRenderMath.notifyBeaconPulse(10, 0.0F), 0.625,
                "notify pulse crosses the midpoint of its 25-100 percent range");
        check(WaypointRenderMath.notifyBeaconPulse(20, 0.0F) == 0.25F,
                "notify pulse stays at 25 percent halfway through its cycle");
        checkClose(WaypointRenderMath.notifyBeaconPulse(30, 0.0F), 0.625,
                "notify pulse returns through the same brightness midpoint");
        check(WaypointRenderMath.notifyBeaconPulse(40, 0.0F) == 1.0F,
                "notify pulse repeats after two seconds");
        check(WaypointRenderMath.notifyBeaconPulse(-20, 0.0F) == 0.25F,
                "notify pulse handles negative world time");
        check(WaypointRenderMath.notifyBeaconPulse(Long.MAX_VALUE, 0.0F)
                        == WaypointRenderMath.notifyBeaconPulse(Math.floorMod(Long.MAX_VALUE, 40L), 0.0F),
                "notify pulse handles very large world time");
        check(Math.abs(WaypointRenderMath.notifyBeaconPulse(9, 0.999999F)
                        - WaypointRenderMath.notifyBeaconPulse(10, 0.0F)) < 0.00001F,
                "partial tick interpolates smoothly across a tick boundary");
        check(WaypointRenderMath.notifyBeaconPulse(20, 0.5F) >= 0.25F
                        && WaypointRenderMath.notifyBeaconPulse(20, 0.5F) <= 1.0F,
                "notify pulse remains bounded at every phase");
        check(WaypointRenderMath.notifyBeaconPulse(0, Float.NaN) == 1.0F,
                "invalid partial tick keeps the pulse bounded");
        for (int tick = -40; tick < 80; tick++) {
            for (int part = 0; part <= 4; part++) {
                float pulse = WaypointRenderMath.notifyBeaconPulse(tick, part / 4.0F);
                check(pulse >= 0.25F && pulse <= 1.0F, "every interpolated phase retains the 25 percent floor");
            }
        }
        checkClose(WaypointRenderMath.beaconOpacity(0.5F, WaypointRenderMath.notifyBeaconPulse(20, 0)),
                0.125, "pulse amplitude multiplies the independent proximity fade");
        check(WaypointRenderMath.beaconOpacity(0, WaypointRenderMath.notifyBeaconPulse(20, 0)) == 0,
                "the animation floor does not prevent a nearby beacon from hiding");
    }

    private static void proximityFade() {
        check(WaypointRenderMath.proximityOpacity(5.0, 5.0) == 0.0F,
                "waypoint is hidden at the configured distance");
        check(WaypointRenderMath.proximityOpacity(2.0, 5.0) == 0.0F,
                "waypoint is hidden closer than the configured distance");
        checkClose(WaypointRenderMath.proximityOpacity(7.5, 5.0), 0.5,
                "default fade midpoint uses smoothstep");
        checkClose(WaypointRenderMath.proximityOpacity(6.25, 5.0), 0.15625,
                "smoothstep has gradual fade near its hidden boundary");
        check(WaypointRenderMath.proximityOpacity(10.0, 5.0) == 1.0F,
                "default fade ends at twice the configured distance");
        check(WaypointRenderMath.proximityOpacity(5.0, 0.0) == 1.0F,
                "zero configured distance disables proximity fading");
        checkClose(WaypointRenderMath.proximityOpacity(15.0, 10.0), 0.5,
                "changing radius moves both fade boundaries");
        check(WaypointRenderMath.proximityOpacity(20.0, 10.0) == 1.0F,
                "changed radius reaches full opacity at twice its value");

        check(WaypointRenderMath.proximityOpacity(3.0, 4.0, 0.0, 5.0) == 0.0F,
                "3D vector distance includes horizontal and vertical components");
        checkClose(WaypointRenderMath.proximityOpacity(0.0, 7.5, 0.0, 5.0), 0.5,
                "vertical separation contributes to proximity");
        check(WaypointRenderMath.proximityOpacity(5.0, 0.0, 0.0, 5.0) == 0.0F,
                "horizontal separation at radius remains hidden");
        check(WaypointRenderMath.proximityOpacity(0.0, 0.0, 0.0, 5.0) == 0.0F,
                "coincident player and waypoint are hidden");
        check(WaypointRenderMath.proximityOpacity(Double.NaN, 5.0) == 0.0F
                        && WaypointRenderMath.proximityOpacity(Double.POSITIVE_INFINITY, 5.0) == 0.0F
                        && WaypointRenderMath.proximityOpacity(-1.0, 5.0) == 0.0F,
                "invalid or negative distances are hidden");
        check(WaypointRenderMath.proximityOpacity(Double.NaN, 0.0, 0.0, 5.0) == 0.0F
                        && WaypointRenderMath.proximityOpacity(0.0, Double.NEGATIVE_INFINITY, 0.0, 5.0) == 0.0F,
                "non-finite vector distances are hidden");
        check(WaypointRenderMath.proximityOpacity(5.0, Double.NaN) == 0.0F
                        && WaypointRenderMath.proximityOpacity(5.0, Double.POSITIVE_INFINITY) == 0.0F
                        && WaypointRenderMath.proximityOpacity(5.0, -1.0) == 0.0F,
                "invalid radii fall back to the five-block default");

        float previous = 0.0F;
        for (int i = 0; i <= 100; i++) {
            float current = WaypointRenderMath.proximityOpacity(5.0 + i * 0.05, 5.0);
            check(current >= previous, "proximity opacity is monotonic");
            previous = current;
        }
        check(WaypointRenderMath.proximityOpacity(4.999999, 5.0) == 0.0F
                        && WaypointRenderMath.proximityOpacity(5.000001, 5.0) < 0.000001F,
                "fade remains continuous at its hidden boundary");
        check(WaypointRenderMath.proximityOpacity(9.999999, 5.0) > 0.999999F
                        && WaypointRenderMath.proximityOpacity(10.0, 5.0) == 1.0F,
                "fade remains continuous at its full-opacity boundary");

        check(Math.abs(WaypointRenderMath.beaconOpacity(0.5F, 0.4F) - 0.2) < 0.000001,
                "notify pulse multiplies only the proximity-faded beam opacity");
        check(WaypointRenderMath.beaconOpacity(0.5F, Float.NaN) == 0.0F,
                "invalid notify pulses cannot produce invalid beam opacity");
    }

    private static void independentLabelFade() {
        check(WaypointRenderMath.labelOpacity(2.5, 2.5) == 1.0F,
                "label fading starts at precisely 2.5 blocks");
        check(WaypointRenderMath.labelOpacity(2.49, 2.5) < 1.0F,
                "labels fade immediately inside the configured start distance");
        check(WaypointRenderMath.labelOpacity(1.25, 2.5) == 0.0F
                        && WaypointRenderMath.labelOpacity(0.0, 2.5) == 0.0F,
                "labels are hidden at half their fade-start distance");
        checkClose(WaypointRenderMath.labelOpacity(1.875, 2.5), 0.5,
                "label fade has its own smoothstep midpoint");
        check(WaypointRenderMath.labelOpacity(0.0, 0.0) == 1.0F,
                "zero disables label fading at a coincident waypoint");
        checkClose(WaypointRenderMath.labelOpacity(6.0, 8.0), 0.5,
                "configured label start moves both label boundaries");
        check(WaypointRenderMath.labelOpacity(0.0, Double.MIN_VALUE) == 0.0F,
                "a positive tiny fade distance does not become the disabled setting");
        for (double invalid : new double[]{-1.0, 128.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            checkClose(WaypointRenderMath.labelOpacity(1.875, invalid), 0.5,
                    "invalid label fade distances fall back to 2.5 blocks");
        }
        for (double invalid : new double[]{-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            check(WaypointRenderMath.labelOpacity(invalid, 0.0) == 0.0F,
                    "invalid label distances stay hidden even with fading disabled");
        }
        WaypointRenderMath.Opacities near = WaypointRenderMath.proximityOpacities(0, 2, 0, 5, 2.5);
        check(near.beam() == 0.0F && near.label() > 0.0F && near.visible(),
                "visible labels remain candidates while their nearby beam is hidden");
        WaypointRenderMath.Opacities beamOnly = WaypointRenderMath.proximityOpacities(0, 7.5, 0, 5, 20);
        checkClose(beamOnly.beam(), 0.5, "default beam midpoint is unchanged");
        check(beamOnly.label() == 0.0F && beamOnly.visible(),
                "a visible beam remains a candidate after its label fades away");
        WaypointRenderMath.Opacities invisible = WaypointRenderMath.proximityOpacities(0, 1.25, 0, 5, 2.5);
        check(!invisible.visible(), "fully hidden waypoints consume no candidate slots");
        WaypointRenderMath.Opacities disabled = WaypointRenderMath.proximityOpacities(0, 0, 0, 0, 0);
        check(disabled.beam() == 1.0F && disabled.label() == 1.0F,
                "beam and label fades can both be disabled");
        check(!WaypointRenderMath.proximityOpacities(Double.NaN, 0, 0, 0, 0).visible(),
                "invalid waypoint vectors consume no candidate slots");
        float previous = 0.0F;
        for (int i = 0; i <= 100; i++) {
            float current = WaypointRenderMath.labelOpacity(1.25 + i * 0.0125, 2.5);
            check(current >= previous && current <= 1.0F, "label fade is monotonic and bounded");
            previous = current;
        }
    }

    private static void alphaScaling() {
        int color = 0x80ABCDEF;
        check(WaypointRenderMath.scaleAlpha(color, 1.0F) == color,
                "full opacity preserves the original ARGB color");
        check(WaypointRenderMath.scaleAlpha(color, 0.0F) == 0x00ABCDEF,
                "zero opacity clears alpha while preserving RGB");
        check(WaypointRenderMath.scaleAlpha(color, 0.5F) == 0x40ABCDEF,
                "half opacity scales alpha and keeps RGB unchanged");
        check(WaypointRenderMath.scaleAlpha(0xFF123456, 0.5F) == 0x80123456,
                "alpha scaling rounds to the nearest byte");
        check(WaypointRenderMath.scaleAlpha(color, Float.NaN) == 0x00ABCDEF,
                "non-finite opacity is safely hidden");
        check(WaypointRenderMath.scaleAlpha(color, -1.0F) == 0x00ABCDEF
                        && WaypointRenderMath.scaleAlpha(color, 2.0F) == color,
                "alpha scaling clamps opacity to its valid range");
    }

    private static void projectedIconCenter() {
        double baseZ = Math.sqrt(4.0 * 4.0 - WaypointRenderMath.ICON_HEIGHT * WaypointRenderMath.ICON_HEIGHT);
        WaypointRenderMath.ViewCandidate<String> near = WaypointRenderMath.projectCandidate(
                "near-icon", 0.0, -WaypointRenderMath.ICON_HEIGHT, baseZ, 120.0);
        checkClose(near.distance(), 4.0, "candidate retains unprojected waypoint distance");
        check(near.dy() == 0.0, "candidate y is the icon center after adding icon height");
        check(near.dz() == baseZ, "near icon center keeps the original projected range");
        check(WaypointRenderMath.lookAngleDegrees(0.0, -WaypointRenderMath.ICON_HEIGHT, baseZ,
                0.0, 0.0) > WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES,
                "near waypoint base vector is outside the focus angle");
        check(WaypointRenderMath.isLookingAt(near.dx(), near.dy(), near.dz(), 0.0, 0.0,
                WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES),
                "near icon center is focused when the waypoint base is not");

        WaypointRenderMath.ViewCandidate<String> clamped = WaypointRenderMath.projectCandidate(
                "clamped", 300.0, 400.0, 0.0, 24.0);
        checkClose(clamped.dx(), 14.4, "projected x uses the distance clamp scale");
        checkClose(clamped.dy(), 20.45, "projected y includes icon height after clamping");
        check(clamped.dz() == 0.0, "projected z uses the distance clamp scale");
        check(clamped.distance() == 500.0, "clamped candidate keeps the original distance");

        WaypointRenderMath.ViewCandidate<String> yawed = WaypointRenderMath.projectCandidate(
                "yawed-icon", -baseZ, -WaypointRenderMath.ICON_HEIGHT, 0.0, 120.0);
        check(WaypointRenderMath.isLookingAt(yawed.dx(), yawed.dy(), yawed.dz(), 90.0, 0.0,
                WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES),
                "icon center focus follows camera yaw");

        double pitchedZ = 3.8;
        double pitchedCenterY = -pitchedZ * Math.tan(Math.toRadians(30.0));
        WaypointRenderMath.ViewCandidate<String> pitched = WaypointRenderMath.projectCandidate(
                "pitched-icon", 0.0, pitchedCenterY - WaypointRenderMath.ICON_HEIGHT,
                pitchedZ, 120.0);
        check(WaypointRenderMath.isLookingAt(pitched.dx(), pitched.dy(), pitched.dz(), 0.0, 30.0,
                WaypointRenderMath.LABEL_LOOK_ANGLE_DEGREES),
                "icon center focus follows camera pitch");

        WaypointRenderMath.ViewCandidate<String> closerButUnfocused = WaypointRenderMath.projectCandidate(
                "closer-but-unfocused", 0.0, 0.0, 2.0, 120.0);
        List<WaypointRenderMath.ViewCandidate<String>> selected = WaypointRenderMath.prioritizeCandidates(
                List.of(closerButUnfocused, near), 0.0, 0.0, 2);
        check(selected.get(0).value().equals("near-icon"),
                "on-icon target is prioritized ahead of a closer off-icon target");
    }

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static void checkClose(double actual, double expected, String description) {
        check(Math.abs(actual - expected) <= 1.0e-9, description);
    }
}
