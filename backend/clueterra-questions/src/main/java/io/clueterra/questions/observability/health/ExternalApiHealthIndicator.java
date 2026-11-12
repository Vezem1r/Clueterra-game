package io.clueterra.questions.observability.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.service.sync.DataSourceClientRegistry;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExternalApiHealthIndicator implements HealthIndicator {

    private final DataSourceClientRegistry clientRegistry;

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        int healthy = 0;
        int unknown = 0;
        for (DataSourceClient client : clientRegistry.enabledClients()) {
            String state = clientRegistry.cachedHealth(client.getSource())
                    .map(up -> up ? "UP" : "DOWN")
                    .orElse("UNKNOWN");
            details.put(client.getSource().name(), state);
            if ("UP".equals(state)) {
                healthy++;
            } else if ("UNKNOWN".equals(state)) {
                unknown++;
            }
        }
        int total = clientRegistry.enabledClients().size();
        details.put("healthyCount", healthy);
        details.put("totalCount", total);
        if (unknown > 0) {
            details.put("message", "Health of " + unknown + " source(s) is still being checked");
        } else if (healthy < total) {
            details.put("message", "Some external APIs are unavailable - sync from them is degraded");
        }
        return Health.up().withDetails(details).build();
    }
}
