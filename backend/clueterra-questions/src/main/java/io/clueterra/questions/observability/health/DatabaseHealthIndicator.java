package io.clueterra.questions.observability.health;

import javax.sql.DataSource;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    private static final long MAX_QUERY_TIME_MS = 1000;

    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();

            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("SELECT 1");
            }

            long queryTime = System.currentTimeMillis() - startTime;

            if (queryTime > MAX_QUERY_TIME_MS) {
                return Health.down()
                        .withDetail("status", "SLOW")
                        .withDetail("queryTimeMs", queryTime)
                        .withDetail("maxQueryTimeMs", MAX_QUERY_TIME_MS)
                        .withDetail("message", "Database responding slowly")
                        .build();
            }

            return Health.up()
                    .withDetail("status", "CONNECTED")
                    .withDetail("queryTimeMs", queryTime)
                    .withDetail("message", "Database connection healthy")
                    .build();

        } catch (Exception e) {
            log.error("Database health check failed", e);
            return Health.down()
                    .withDetail("status", "DISCONNECTED")
                    .withDetail("error", e.getMessage())
                    .withDetail("message", "Database connection failed")
                    .build();
        }
    }
}
