package com.haval.akppmodes.trip;

public final class TripSummaryDismissController {
    public interface Host {
        void schedule(Runnable task, long delayMs);
        void cancel(Runnable task);
        void animateOut(Runnable completion);
        void remove();
    }

    private static final long AUTO_DISMISS_DELAY_MS = 10_000L;

    private final Host host;
    private final Runnable timeout = this::dismiss;

    private boolean attached;
    private boolean dismissing;
    private boolean removed;

    public TripSummaryDismissController(Host host) { this.host = host; }

    public void attach() {
        if (attached || removed) return;
        attached = true;
        host.schedule(timeout, AUTO_DISMISS_DELAY_MS);
    }

    public void detach() {
        if (!attached) return;
        attached = false;
        host.cancel(timeout);
    }

    public void dismiss() {
        if (!attached || dismissing || removed) return;
        dismissing = true;
        host.cancel(timeout);
        host.animateOut(this::finishDismiss);
    }

    private void finishDismiss() {
        if (!attached || removed) return;
        removed = true;
        host.remove();
    }
}