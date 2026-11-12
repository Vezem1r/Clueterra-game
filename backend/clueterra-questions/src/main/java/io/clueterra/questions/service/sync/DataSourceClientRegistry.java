package io.clueterra.questions.service.sync;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import io.clueterra.questions.clients.common.DataSourceClient;
import io.clueterra.questions.config.AppProperties;
import io.clueterra.questions.config.SourcesProperties;
import io.clueterra.questions.entity.enums.DataSource;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DataSourceClientRegistry {

    private final Map<DataSource, DataSourceClient> clientRegistry;
    private final List<DataSourceClient> configuredEnabledClients;
    private final SourcesProperties sourcesProperties;
    private final long healthCheckIntervalMs;
    private final ExecutorService executorService;

    private final Map<DataSource, Boolean> healthCache = new ConcurrentHashMap<>();
    private volatile long lastHealthCheck = 0;

    public DataSourceClientRegistry(List<DataSourceClient> clients,
                                    SourcesProperties sourcesProperties,
                                    AppProperties appProperties) {
        this.sourcesProperties = sourcesProperties;
        this.clientRegistry = clients.stream().collect(Collectors.toMap(DataSourceClient::getSource, Function.identity()));
        this.configuredEnabledClients = clients.stream()
                .filter(client -> isEnabled(client.getSource()))
                .toList();
        this.healthCheckIntervalMs = Duration.ofHours(appProperties.getHealth().getHourTtl()).toMillis();

        AtomicInteger threadCounter = new AtomicInteger();
        this.executorService = Executors.newFixedThreadPool(
                Math.clamp(configuredEnabledClients.size(), 1, 5),
                runnable -> {
                    Thread thread = new Thread(runnable, "datasource-health-" + threadCounter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        log.info("DataSourceClientRegistry initialized with {} enabled clients, health TTL {} ms",
                configuredEnabledClients.size(), healthCheckIntervalMs);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpHealthCache() {
        refreshHealthCacheIfNeeded();
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Optional<DataSourceClient> getClient(DataSource source) {
        return Optional.ofNullable(clientRegistry.get(source));
    }

    public void checkAllHealth() {
        List<CompletableFuture<Void>> futures = configuredEnabledClients.stream()
                .map(client -> CompletableFuture.runAsync(() -> healthCache.put(client.getSource(), safeIsAvailable(client)),
                        executorService))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        lastHealthCheck = System.currentTimeMillis();

        long healthyCount = healthCache.values().stream().filter(Boolean::booleanValue).count();
        log.info("Health check complete: {}/{} sources healthy", healthyCount, configuredEnabledClients.size());
    }

    public boolean isEnabled(DataSource source) {
        try {
            return sourcesProperties.getSource(source).isEnabled();
        } catch (IllegalStateException e) {
            log.debug("No source properties found for {} while checking enabled status", source);
            return false;
        }
    }

    public Optional<Boolean> cachedHealth(DataSource source) {
        refreshHealthCacheIfNeeded();
        return isEnabled(source) ? Optional.ofNullable(healthCache.get(source)) : Optional.of(false);
    }

    public boolean isHealthy(DataSource source) {
        return cachedHealth(source).orElse(false);
    }

    public List<DataSourceClient> enabledClients() {
        return configuredEnabledClients;
    }

    private boolean safeIsAvailable(DataSourceClient client) {
        try {
            return client.isAvailable();
        } catch (RuntimeException e) {
            log.warn("Health check failed for {}", client.getSource(), e);
            return false;
        }
    }

    private void refreshHealthCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheck > healthCheckIntervalMs) {
            lastHealthCheck = now;
            CompletableFuture.runAsync(this::checkAllHealth, executorService)
                    .exceptionally(e -> {
                        log.warn("Background health check failed", e);
                        return null;
                    });
        }
    }
}
