package io.clueterra.questions.service.sync;

import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SyncProgressReporterTest {

    @Test
    void info_formatsAllContextParts() {
        ConsoleLogWriter console = mock(ConsoleLogWriter.class);
        SyncProgressReporter reporter = new SyncProgressReporter(console);

        reporter.info("op-1", "FETCH", DataSource.MANUAL, "US", 1, 20, "Fetching slice");

        verify(console).info("[FETCH] [MANUAL] [US] (1/20) Fetching slice");
    }

    @Test
    void success_omitsBlankOptionalParts() {
        ConsoleLogWriter console = mock(ConsoleLogWriter.class);
        SyncProgressReporter reporter = new SyncProgressReporter(console);

        reporter.success(null, "COMPLETE", DataSource.WORLD_BANK, null, null, null, "Done");

        verify(console).success("[COMPLETE] [WORLD_BANK] Done");
    }

    @Test
    void error_includesOnlyAvailableParts() {
        ConsoleLogWriter console = mock(ConsoleLogWriter.class);
        SyncProgressReporter reporter = new SyncProgressReporter(console);

        reporter.error("op-2", null, null, "JP", 2, 5, "Failed");

        verify(console).error("[JP] (2/5) Failed");
    }
}
