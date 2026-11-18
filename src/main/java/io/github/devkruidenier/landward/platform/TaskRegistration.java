package io.github.devkruidenier.landward.platform;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Cancellation can win before a platform has returned its task handle. */
public final class TaskRegistration {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean delivered = new AtomicBoolean();
    private final AtomicReference<Runnable> cancellation = new AtomicReference<>();
    public void bind(Runnable action) {
        if (!cancellation.compareAndSet(null, action)) throw new IllegalStateException("Task already bound");
        deliver();
    }
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            deliver();
        }
    }
    private void deliver() {
        Runnable action = cancellation.get();
        if (cancelled.get() && action != null && delivered.compareAndSet(false, true)) action.run();
    }
    public boolean cancelled() { return cancelled.get(); }
}
