// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.compat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Headless warm benchmark for the synchronized transit discovery and clustering path. */
public final class TransitStationBenchmark {
    private static final int WARMUPS = 3;
    private static final int SAMPLES = 11;
    private static long generation;

    private TransitStationBenchmark() {}

    public static void main(String[] args) throws Exception {
        for (int size : new int[]{100, 1_000, 10_000}) {
            benchmarkCreate("sparse", size, Shape.SPARSE);
            benchmarkCreateUnchanged(size);
            benchmarkCreate("dense", size, Shape.DENSE);
            benchmarkCreate("chain", size, Shape.CHAIN);
            benchmarkMtr(size);
            benchmarkMtrUnchanged(size);
        }
    }

    private static void benchmarkCreate(String label, int size, Shape shape) throws Exception {
        long[] samples = new long[SAMPLES];
        int count = WARMUPS + SAMPLES;
        for (int i = 0; i < count; i++) {
            BenchmarkRailway input = createInput(size, shape, ++generation);
            long start = System.nanoTime();
            List<TransitStation> result = discoverCreate(input);
            long elapsed = System.nanoTime() - start;
            if (result.isEmpty()) throw new AssertionError("Create benchmark produced no stations");
            if (i >= WARMUPS) samples[i - WARMUPS] = elapsed;
        }
        report("Create " + label, size, samples);
    }

    private static void benchmarkCreateUnchanged(int size) throws Exception {
        BenchmarkRailway input = createInput(size, Shape.SPARSE, ++generation);
        discoverCreate(input);
        long[] samples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime();
            List<TransitStation> result = discoverCreate(input);
            samples[i] = System.nanoTime() - start;
            if (result.isEmpty()) throw new AssertionError("Unchanged Create snapshot produced no stations");
        }
        report("Create unchanged", size, samples);
    }

    private static BenchmarkRailway createInput(int size, Shape shape, long generation) {
        List<BenchmarkCreateStation> stations = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int x, y, z;
            switch (shape) {
                case SPARSE -> { x = i * 100; y = 0; z = -i * 100; }
                case DENSE -> { x = i % 10; y = i / 10 % 10; z = i / 100 % 10; }
                case CHAIN -> { x = i * 25; y = 0; z = 0; }
                default -> throw new AssertionError(shape);
            }
            stations.add(new BenchmarkCreateStation(generation * 20_000L + i, "Benchmark", "overworld",
                    new TransitStation.Position(x, y, z)));
        }
        return new BenchmarkRailway(stations);
    }

    private static List<TransitStation> discoverCreate(BenchmarkRailway railway) throws Exception {
        List<TransitStation> discovered = TransitStationReader.readCreate(railway, "station",
                value -> (TransitStation.Position) value, (station, dimension, position) -> true);
        return TransitStationCatalog.unique(discovered, Object::toString, 50);
    }

    private static void benchmarkMtr(int size) throws Exception {
        long[] samples = new long[SAMPLES];
        int count = WARMUPS + SAMPLES;
        for (int i = 0; i < count; i++) {
            BenchmarkMtrData data = mtrData(size, ++generation);
            long start = System.nanoTime();
            List<TransitStation> result = TransitStationReader.readMtr4(data, "overworld", value -> (TransitStation.Position) value);
            long elapsed = System.nanoTime() - start;
            if (result.size() != 1) throw new AssertionError("MTR benchmark did not combine platforms into one station");
            if (i >= WARMUPS) samples[i - WARMUPS] = elapsed;
        }
        report("MTR platforms", size, samples);
    }

    private static void benchmarkMtrUnchanged(int size) throws Exception {
        BenchmarkMtrData data = mtrData(size, ++generation);
        TransitStationReader.readMtr4(data, "overworld", value -> (TransitStation.Position) value);
        long[] samples = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime();
            List<TransitStation> result = TransitStationReader.readMtr4(data, "overworld", value -> (TransitStation.Position) value);
            samples[i] = System.nanoTime() - start;
            if (result.size() != 1) throw new AssertionError("Unchanged MTR snapshot lost its station");
        }
        report("MTR unchanged", size, samples);
    }

    private static BenchmarkMtrData mtrData(int size, long generation) {
        BenchmarkArea area = new BenchmarkArea(generation);
        List<BenchmarkPlatform> platforms = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int x = i % 10 - 5;
            int y = i / 10 % 10 - 5;
            int z = i / 100 % 10 - 5;
            platforms.add(new BenchmarkPlatform(generation * 20_000L + i, area,
                    new TransitStation.Position(x, y, z)));
        }
        return new BenchmarkMtrData(platforms);
    }

    private static void report(String scenario, int size, long[] samples) {
        Arrays.sort(samples);
        long median = samples[samples.length / 2];
        long p95 = samples[(int) Math.ceil(samples.length * 0.95) - 1];
        System.out.printf("%-18s %5d stations | median %8.2f ms | p95 %8.2f ms%n", scenario, size,
                median / 1_000_000.0, p95 / 1_000_000.0);
    }

    private enum Shape { SPARSE, DENSE, CHAIN }

    public static final class BenchmarkRailway {
        public final java.util.Map<String, BenchmarkGraph> trackNetworks;
        BenchmarkRailway(List<BenchmarkCreateStation> stations) {
            trackNetworks = java.util.Map.of("network", new BenchmarkGraph(stations));
        }
    }

    public static final class BenchmarkGraph {
        private final List<BenchmarkCreateStation> stations;
        BenchmarkGraph(List<BenchmarkCreateStation> stations) { this.stations = stations; }
        public List<BenchmarkCreateStation> getPoints(Object type) { return stations; }
    }

    public static final class BenchmarkCreateStation {
        private final long id;
        public final String name;
        public final Object edgeLocation;
        private final String dimension;
        private final TransitStation.Position position;
        BenchmarkCreateStation(long id, String name, String dimension, TransitStation.Position position) {
            this.id = id; this.name = name; this.dimension = dimension; this.position = position;
            edgeLocation = new BenchmarkEdge(new BenchmarkNode(dimension), new BenchmarkNode(dimension));
        }
        public TransitStation.Position getBlockEntityPos() { return position; }
        public String getBlockEntityDimension() { return dimension; }
        public long getId() { return id; }
    }

    public static final class BenchmarkEdge {
        private final BenchmarkNode first, second;
        BenchmarkEdge(BenchmarkNode first, BenchmarkNode second) { this.first = first; this.second = second; }
        public BenchmarkNode getFirst() { return first; }
        public BenchmarkNode getSecond() { return second; }
    }

    public static final class BenchmarkNode {
        private final String dimension;
        BenchmarkNode(String dimension) { this.dimension = dimension; }
        public String getDimension() { return dimension; }
    }

    public static final class BenchmarkMtrData {
        public final List<BenchmarkPlatform> platforms;
        BenchmarkMtrData(List<BenchmarkPlatform> platforms) { this.platforms = platforms; }
    }

    public static final class BenchmarkArea {
        private final long id;
        BenchmarkArea(long id) { this.id = id; }
        public long getId() { return id; }
        public String getName() { return "Benchmark"; }
        public boolean inArea(TransitStation.Position position) { return true; }
    }

    public static final class BenchmarkPlatform {
        private final long id;
        public final BenchmarkArea area;
        private final TransitStation.Position position;
        BenchmarkPlatform(long id, BenchmarkArea area, TransitStation.Position position) {
            this.id = id; this.area = area; this.position = position;
        }
        public long getId() { return id; }
        public TransitStation.Position getMidPosition() { return position; }
        public boolean isInvalidSavedRail(BenchmarkMtrData data) { return false; }
    }
}
