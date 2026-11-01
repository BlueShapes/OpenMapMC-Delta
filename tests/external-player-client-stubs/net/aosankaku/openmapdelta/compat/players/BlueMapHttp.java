// Copyright (c) 2026 AoSankaku. Licensed under Apache-2.0.
package net.aosankaku.openmapdelta.compat.players;

import net.aosankaku.openmapdelta.network.PlayerPosition;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Controllable asynchronous transport stub for controller tests; never opens a socket. */
public final class BlueMapHttp {
    private static final ConcurrentLinkedQueue<Plan> PLANS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger COMPLETED = new AtomicInteger();

    private BlueMapHttp() { }

    public static Plan hold(List<PlayerPosition> players) { return new Plan(players, false); }
    public static Plan immediate(List<PlayerPosition> players) { return new Plan(players, true); }
    public static void enqueue(Plan plan) { PLANS.add(plan); }
    public static int lastCompletedCount() { return COMPLETED.get(); }
    public static void clearPlans() { PLANS.clear(); COMPLETED.set(0); }

    public static final class Request {
        private final Plan plan;
        public Request(BlueMapEndpoint endpoint, String dimension) {
            Plan next = PLANS.poll();
            plan = next == null ? immediate(List.of()) : next;
        }
        public List<PlayerPosition> fetch() throws IOException {
            try { return plan.fetch(); }
            finally { COMPLETED.incrementAndGet(); }
        }
        /** Deliberately allows an already-running response to complete, modelling an I/O race. */
        public void cancel() { }
    }

    public static final class Plan {
        private final List<PlayerPosition> players;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);

        private Plan(List<PlayerPosition> players, boolean immediate) {
            this.players = List.copyOf(players);
            if (immediate) released.countDown();
        }

        private List<PlayerPosition> fetch() throws IOException {
            started.countDown();
            boolean interrupted = false;
            try {
                while (true) {
                    try { released.await(); break; }
                    catch (InterruptedException ignored) { interrupted = true; }
                }
                return players;
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
                finished.countDown();
            }
        }

        public boolean awaitStarted() throws InterruptedException { return started.await(2, TimeUnit.SECONDS); }
        public boolean awaitFinished() throws InterruptedException { return finished.await(2, TimeUnit.SECONDS); }
        public void release() { released.countDown(); }
    }
}
