// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.gui;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ControllerNavigationBridgeTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        optionalClassLoaderAbsenceIsSilent();
        modernObjectRegistrationAndIdempotence();
        legacyMarkerRegistration();
        nullProviderPreservesExistingTargets();
        emptyProviderClearsModalTargets();
        failedConstructionDoesNotPartiallyReplace();
        providerFailurePreservesTargets();
        malformedListenerIsContained();
        proxyObjectMethodsUseIdentity();
        System.out.println("Controller navigation bridge tests passed (" + assertions + " assertions).");
    }

    private static void optionalClassLoaderAbsenceIsSilent() {
        ControllerNavigationBridge bridge = new ControllerNavigationBridge();
        AtomicInteger failures = new AtomicInteger();
        ClassLoader emptyLoader = new ClassLoader(null) { };
        check(!bridge.connect(emptyLoader, List::of, ignored -> failures.incrementAndGet()),
                "missing optional API returns false");
        check(failures.get() == 0 && !bridge.isConnected(), "missing optional API is silent");

        ControllerNavigationBridge brokenBridge = new ControllerNavigationBridge();
        AtomicInteger linkageFailures = new AtomicInteger();
        ClassLoader brokenLoader = new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("com.mrcrayfish.controllable.event.ControllerEvents")) {
                    throw new NoClassDefFoundError("optional API dependency missing");
                }
                return super.loadClass(name, resolve);
            }
        };
        check(!brokenBridge.connect(brokenLoader, List::of, ignored -> {
            linkageFailures.incrementAndGet();
            throw new IllegalStateException("reporting callback failed");
        }), "linkage failure is contained");
        check(linkageFailures.get() == 1 && !brokenBridge.isConnected(),
                "linkage failure is reported once even if the reporter throws");
    }

    private static void modernObjectRegistrationAndIdempotence() throws Exception {
        ControllerNavigationBridge bridge = new ControllerNavigationBridge();
        ModernEvent event = new ModernEvent();
        AtomicInteger failures = new AtomicInteger();
        Supplier<List<ControllerNavigationLayout.Point>> provider = () -> List.of(point(10, 20));
        boolean connected = bridge.connect(event, Listener.class, BasicPoint.class.getConstructor(double.class, double.class),
                provider, ignored -> failures.incrementAndGet());
        check(connected && bridge.isConnected(), "modern Object registration succeeds");
        check(bridge.connect(event, Listener.class, BasicPoint.class.getConstructor(double.class, double.class),
                provider, ignored -> failures.incrementAndGet()), "same event connection is idempotent");
        check(event.registerCalls == 1 && failures.get() == 0, "idempotent connect does not register twice");
        List<Object> targets = new ArrayList<>(List.of("old"));
        event.listener.handle(targets);
        check(targets.size() == 1 && targets.get(0) instanceof BasicPoint,
                "modern callback replaces targets with optional navigation points");
        BasicPoint navigation = (BasicPoint) targets.get(0);
        check(navigation.x == 10 && navigation.y == 20, "point constructor receives screen coordinates");
    }

    private static void legacyMarkerRegistration() throws Exception {
        ControllerNavigationBridge bridge = new ControllerNavigationBridge();
        LegacyEvent event = new LegacyEvent();
        check(bridge.connect(event, LegacyListener.class,
                BasicPoint.class.getConstructor(double.class, double.class), List::of, ignored -> { }),
                "legacy IFrameworkEvent-style marker registration succeeds");
        check(event.registerCalls == 1 && event.listener != null, "legacy listener was registered");
    }

    private static void nullProviderPreservesExistingTargets() throws Exception {
        ControllerNavigationBridge bridge = connectedBridge(new ModernEvent(), () -> null, ignored -> { });
        ModernEvent event = (ModernEvent) registeredEvent(bridge);
        List<Object> targets = new ArrayList<>(List.of("keep", "also keep"));
        event.listener.handle(targets);
        check(targets.equals(List.of("keep", "also keep")), "null provider leaves non-map points untouched");
    }

    private static void emptyProviderClearsModalTargets() throws Exception {
        ControllerNavigationBridge bridge = connectedBridge(new ModernEvent(), List::of, ignored -> { });
        ModernEvent event = (ModernEvent) registeredEvent(bridge);
        List<Object> targets = new ArrayList<>(List.of("old", "menu"));
        event.listener.handle(targets);
        check(targets.isEmpty(), "empty provider clears points for a modal with no navigation options");
    }

    private static void failedConstructionDoesNotPartiallyReplace() throws Exception {
        ControllerNavigationBridge bridge = new ControllerNavigationBridge();
        ModernEvent event = new ModernEvent();
        AtomicInteger failures = new AtomicInteger();
        check(bridge.connect(event, Listener.class, ThrowingPoint.class.getConstructor(double.class, double.class),
                () -> List.of(point(1, 1), point(2, 2)), ignored -> failures.incrementAndGet()),
                "bridge registers before callback construction");
        List<Object> targets = new ArrayList<>(List.of("original-a", "original-b"));
        event.listener.handle(targets);
        check(targets.equals(List.of("original-a", "original-b")),
                "a later point-constructor throw preserves the complete original list");
        check(failures.get() == 1, "point construction failure is reported once");
    }

    private static void providerFailurePreservesTargets() throws Exception {
        AtomicInteger failures = new AtomicInteger();
        ControllerNavigationBridge bridge = connectedBridge(new ModernEvent(), () -> {
            throw new IllegalStateException("screen lookup failed");
        }, ignored -> {
            failures.incrementAndGet();
            throw new IllegalStateException("reporting callback failed");
        });
        ModernEvent event = (ModernEvent) registeredEvent(bridge);
        List<Object> targets = new ArrayList<>(List.of("keep"));
        event.listener.handle(targets);
        event.listener.handle(targets);
        check(targets.equals(List.of("keep")), "provider failure preserves targets");
        check(failures.get() == 1, "provider errors are contained and reported once");
    }

    private static void malformedListenerIsContained() throws Exception {
        ControllerNavigationBridge bridge = new ControllerNavigationBridge();
        AtomicInteger failures = new AtomicInteger();
        check(!bridge.connect(new ModernEvent(), MissingHandle.class,
                BasicPoint.class.getConstructor(double.class, double.class), List::of,
                ignored -> failures.incrementAndGet()), "malformed interface is rejected");
        check(failures.get() == 1 && !bridge.isConnected(), "malformed optional API is reported without connecting");
    }

    private static void proxyObjectMethodsUseIdentity() throws Exception {
        ControllerNavigationBridge bridge = connectedBridge(new ModernEvent(), List::of, ignored -> { });
        ModernEvent event = (ModernEvent) registeredEvent(bridge);
        Object listener = event.listener;
        check(listener.equals(listener), "proxy equals itself");
        check(!listener.equals(new Object()), "proxy equality is identity based");
        check(listener.hashCode() == System.identityHashCode(listener), "proxy hash code is identity based");
        check(listener.toString().contains("ControllerNavigationBridge.Listener@"), "proxy has a safe diagnostic string");
    }

    private static ControllerNavigationBridge connectedBridge(ModernEvent event,
            Supplier<List<ControllerNavigationLayout.Point>> provider, Consumer<Throwable> onFailure) throws Exception {
        ControllerNavigationBridge bridge = new ControllerNavigationBridge();
        check(bridge.connect(event, Listener.class, BasicPoint.class.getConstructor(double.class, double.class),
                provider, onFailure), "test event connects");
        return bridge;
    }

    private static Object registeredEvent(ControllerNavigationBridge bridge) throws Exception {
        // The event field is private by design; use a dedicated modern event retained by the test setup instead.
        var field = ControllerNavigationBridge.class.getDeclaredField("registeredEvent");
        field.setAccessible(true);
        return field.get(bridge);
    }

    private static ControllerNavigationLayout.Point point(double x, double y) {
        return new ControllerNavigationLayout.Point(x, y);
    }

    private static void check(boolean success, String message) {
        if (!success) throw new AssertionError(message);
        assertions++;
    }

    public interface Listener {
        void handle(List<?> points);
    }

    public interface LegacyMarker {
    }

    public interface LegacyListener extends LegacyMarker {
        void handle(List<?> points);
    }

    public interface MissingHandle {
        void somethingElse();
    }

    public static final class BasicPoint {
        final double x;
        final double y;

        public BasicPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class ThrowingPoint {
        public ThrowingPoint(double x, double y) {
            if (x == 2) throw new IllegalStateException("second point rejected");
        }
    }

    public static final class ModernEvent {
        int registerCalls;
        Listener listener;

        public void register(Object listener) {
            registerCalls++;
            this.listener = (Listener) listener;
        }
    }

    public static final class LegacyEvent {
        int registerCalls;
        LegacyListener listener;

        public void register(LegacyMarker listener) {
            registerCalls++;
            this.listener = (LegacyListener) listener;
        }
    }
}
