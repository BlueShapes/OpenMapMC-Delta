// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import org.mtr.core.data.ClientData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Angle;
import org.mtr.core.data.TransportMode;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Map;

/** Headless geometry regression against an installed MTR 4 JAR, without starting Minecraft. */
public final class TransitMtrRailJarTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        ClientData data = new ClientData();
        Position start = new Position(10, 65, 20);
        Position end = new Position(30, 65, 20);
        Rail straight = Rail.newPlatformRail(start, Angle.E, end, Angle.W, Rail.Shape.QUADRATIC,
                0D, new ObjectArrayList<>(), TransportMode.TRAIN);
        check(straight.isValid(), "installed MTR straight train rail is valid");
        data.rails.add(straight);

        TransitRailReader.MtrSnapshot straightSnapshot = TransitRailReader.readMtr4(data, "overworld",
                TransitMtrRailJarTest::readVector, Map.of());
        check(straightSnapshot.rails().size() == 1, "reader accepts actual installed MTR client rail data");
        TransitRail.Point first = straightSnapshot.rails().get(0).points().get(0);
        TransitRail.Point last = straightSnapshot.rails().get(0).points().get(1);
        check(close(first.x(), 10.5) && close(first.z(), 20.5)
                        && close(last.x(), 30.5) && close(last.z(), 20.5),
                "straight rail endpoints keep MTR's native half-block offset");

        data.rails.clear();
        Position curveStart = new Position(0, 65, 0);
        Position curveEnd = new Position(30, 65, 30);
        Rail curve = Rail.newPlatformRail(curveStart, Angle.E, curveEnd, Angle.N, Rail.Shape.QUADRATIC,
                0D, new ObjectArrayList<>(), TransportMode.TRAIN);
        check(curve.isValid(), "installed MTR quarter-turn rail is valid");
        data.rails.add(curve);
        TransitRailReader.MtrSnapshot curvedSnapshot = TransitRailReader.readMtr4(data, "overworld",
                TransitMtrRailJarTest::readVector, Map.of());
        check(curvedSnapshot.rails().size() == 1 && curvedSnapshot.rails().get(0).points().size() > 2,
                "actual MTR curve is retained as sampled track geometry");
        TransitRail.Point midpoint = curvedSnapshot.rails().get(0).points().get(
                curvedSnapshot.rails().get(0).points().size() / 2);
        check(close(midpoint.x(), 21.7132034356) && close(midpoint.z(), 9.2867965644),
                "sampled actual MTR curve passes through its real middle position");

        System.out.println("Installed MTR JAR rail checks passed (" + assertions + " assertions).");
    }

    private static TransitRail.Point readVector(Object vector) throws ReflectiveOperationException {
        return new TransitRail.Point(coordinate(vector, "x"), coordinate(vector, "y"), coordinate(vector, "z"));
    }

    private static double coordinate(Object vector, String axis) throws ReflectiveOperationException {
        Object coordinate;
        try { coordinate = TransitStationReader.call(vector, axis); }
        catch (NoSuchMethodException noAccessor) { coordinate = TransitStationReader.field(vector, axis); }
        if (!(coordinate instanceof Number number)) throw new NoSuchFieldException("MTR vector " + axis);
        return number.doubleValue();
    }

    private static boolean close(double actual, double expected) { return Math.abs(actual - expected) < 1e-6; }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
