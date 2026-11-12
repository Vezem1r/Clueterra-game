package io.clueterra.questions.service.sync;

import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.service.console.ConsoleLogWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncProgressReporter {

    private final ConsoleLogWriter console;

    public void info(String operationId,
                     String stage,
                     DataSource source,
                     String countryCode,
                     Integer current,
                     Integer total,
                     String message) {
        console.info(formatForConsole(stage, source, countryCode, current, total, message));
        log.debug(formatForLog(operationId, stage, source, countryCode, current, total, message));
    }

    public void success(String operationId,
                        String stage,
                        DataSource source,
                        String countryCode,
                        Integer current,
                        Integer total,
                        String message) {
        console.success(formatForConsole(stage, source, countryCode, current, total, message));
        log.debug(formatForLog(operationId, stage, source, countryCode, current, total, message));
    }

    public void error(String operationId,
                      String stage,
                      DataSource source,
                      String countryCode,
                      Integer current,
                      Integer total,
                      String message) {
        console.error(formatForConsole(stage, source, countryCode, current, total, message));
        log.debug(formatForLog(operationId, stage, source, countryCode, current, total, message));
    }

    private String formatForConsole(String stage,
                                    DataSource source,
                                    String countryCode,
                                    Integer current,
                                    Integer total,
                                    String message) {
        return format(null, stage, source, countryCode, current, total, message);
    }

    private String formatForLog(String operationId,
                                String stage,
                                DataSource source,
                                String countryCode,
                                Integer current,
                                Integer total,
                                String message) {
        return format(operationId, stage, source, countryCode, current, total, message);
    }

    private String format(String operationId,
                          String stage,
                          DataSource source,
                          String countryCode,
                          Integer current,
                          Integer total,
                          String message) {
        StringBuilder builder = new StringBuilder();
        if (operationId != null && !operationId.isBlank()) {
            builder.append("[op=").append(operationId).append("] ");
        }
        if (stage != null && !stage.isBlank()) {
            builder.append("[").append(stage).append("] ");
        }
        if (source != null) {
            builder.append("[").append(source.name()).append("] ");
        }
        if (countryCode != null && !countryCode.isBlank()) {
            builder.append("[").append(countryCode).append("] ");
        }
        if (current != null && total != null && total > 0) {
            builder.append("(").append(current).append("/").append(total).append(") ");
        }
        builder.append(message);
        return builder.toString();
    }
}
