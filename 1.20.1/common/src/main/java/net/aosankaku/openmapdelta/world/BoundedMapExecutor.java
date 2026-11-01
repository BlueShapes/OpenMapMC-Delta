// Copyright (c) 2026 AoSankaku. OpenMapMC Delta changes; derived from Odyssey Map by NAIZO under Apache-2.0.
package net.aosankaku.openmapdelta.world;

import java.util.Set;
import java.util.concurrent.*;

/** Shared, bounded CPU pool; cancellation also frees jobs that have not started. */
public final class BoundedMapExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Semaphore slots;
    private final Set<FutureTask<?>> tasks = ConcurrentHashMap.newKeySet();
    private boolean closed;

    public BoundedMapExecutor(int workers) {
        if (workers < 1 || workers > 4) throw new IllegalArgumentException("workers must be 1-4");
        slots = new Semaphore(workers * 2);
        executor = new ThreadPoolExecutor(workers, workers, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "OpenMapDelta-chunk-generation");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
    }

    public synchronized boolean hasCapacity() { return !closed && slots.availablePermits() > 0; }

    public synchronized <T> Future<T> trySubmit(Callable<T> work) {
        if (closed || !slots.tryAcquire()) return null;
        FutureTask<T> task = new FutureTask<>(work) {
            @Override protected void done() {
                tasks.remove(this);
                executor.remove(this);
                slots.release();
            }
        };
        tasks.add(task);
        executor.execute(task);
        return task;
    }

    public static int workerCount(int cores, double cpuLoad) {
        if (cores < 4 || (Double.isFinite(cpuLoad) && cpuLoad >= 0.80)) return 0;
        return Math.min(4, cores - 2);
    }

    @Override public synchronized void close() {
        closed = true;
        for (FutureTask<?> task : tasks) task.cancel(true);
        executor.shutdownNow();
    }
}
