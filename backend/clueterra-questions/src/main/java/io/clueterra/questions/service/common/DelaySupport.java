package io.clueterra.questions.service.common;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

public final class DelaySupport {

    private DelaySupport() {
    }

    public static void pause(Duration duration) throws InterruptedException {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }

        long remainingNanos = duration.toNanos();
        long deadline = System.nanoTime() + remainingNanos;
        while (remainingNanos > 0L) {
            LockSupport.parkNanos(remainingNanos);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Pause interrupted");
            }
            remainingNanos = deadline - System.nanoTime();
        }
    }
}
