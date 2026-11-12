package io.clueterra.questions.support;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class VerboseTestWatcher implements TestWatcher, AfterAllCallback {

    private static final AtomicInteger PASSED = new AtomicInteger();
    private static final AtomicInteger FAILED = new AtomicInteger();
    private static final AtomicInteger ABORTED = new AtomicInteger();
    private static final AtomicInteger SKIPPED = new AtomicInteger();

    @Override
    public void testSuccessful(ExtensionContext context) {
        PASSED.incrementAndGet();
        System.out.printf("[TEST][PASS] %s :: %s%n",
                context.getRequiredTestClass().getSimpleName(),
                context.getDisplayName());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        FAILED.incrementAndGet();
        System.out.printf("[TEST][FAIL] %s :: %s -- %s%n",
                context.getRequiredTestClass().getSimpleName(),
                context.getDisplayName(),
                cause != null ? cause.getMessage() : "unknown failure");
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        ABORTED.incrementAndGet();
        System.out.printf("[TEST][ABORTED] %s :: %s%n",
                context.getRequiredTestClass().getSimpleName(),
                context.getDisplayName());
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        SKIPPED.incrementAndGet();
        System.out.printf("[TEST][SKIPPED] %s :: %s%n",
                context.getRequiredTestClass().getSimpleName(),
                context.getDisplayName());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (context.getParent().isPresent()) {
            return;
        }

        int passed = PASSED.get();
        int failed = FAILED.get();
        int aborted = ABORTED.get();
        int skipped = SKIPPED.get();
        int total = passed + failed + aborted + skipped;

        System.out.printf("[TEST][SUMMARY] total=%d, passed=%d, failed=%d, aborted=%d, skipped=%d%n",
                total, passed, failed, aborted, skipped);
    }
}
