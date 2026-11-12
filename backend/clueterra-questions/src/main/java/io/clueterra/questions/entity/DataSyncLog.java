package io.clueterra.questions.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.clueterra.questions.clients.common.dto.FetchFailure;
import io.clueterra.questions.entity.enums.DataSource;
import io.clueterra.questions.entity.enums.SyncStatus;
import io.clueterra.questions.entity.enums.SyncType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataSyncLog {

    private Long id;
    private Long batchId;
    private String countryCode;
    private DataSource source;
    private SyncType syncType;
    private SyncStatus status;

    @Builder.Default
    private Integer countriesProcessed = 0;

    @Builder.Default
    private Integer statisticsSaved = 0;

    @Builder.Default
    private Integer hintsGenerated = 0;

    @Builder.Default
    private Integer hintsDeactivated = 0;

    private String errorMessage;
    private String errorStack;
    private String fetchDetails;

    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;

    @Builder.Default
    private transient List<FetchFailure> fetchErrors = new ArrayList<>();

    public static DataSyncLog start(DataSource source, SyncType syncType) {
        return DataSyncLog.builder()
                .source(source)
                .syncType(syncType)
                .status(SyncStatus.STARTED)
                .startedAt(Instant.now())
                .countriesProcessed(0)
                .statisticsSaved(0)
                .hintsGenerated(0)
                .hintsDeactivated(0)
                .build();
    }

    public static DataSyncLog start(DataSource source, SyncType syncType, Long batchId, String countryCode) {
        return DataSyncLog.builder()
                .source(source)
                .syncType(syncType)
                .batchId(batchId)
                .countryCode(countryCode)
                .status(SyncStatus.STARTED)
                .startedAt(Instant.now())
                .countriesProcessed(0)
                .statisticsSaved(0)
                .hintsGenerated(0)
                .hintsDeactivated(0)
                .build();
    }

    public void complete() {
        this.status = SyncStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.durationMs = Duration.between(startedAt, completedAt).toMillis();
    }

    public void fail(Exception e) {
        this.status = SyncStatus.FAILED;
        this.completedAt = Instant.now();
        this.durationMs = Duration.between(startedAt, completedAt).toMillis();
        this.errorMessage = e.getMessage();

        if (e.getStackTrace().length > 0) {
            StringBuilder sb = new StringBuilder();
            int maxLines = Math.min(10, e.getStackTrace().length);
            for (int i = 0; i < maxLines; i++) {
                sb.append(e.getStackTrace()[i].toString()).append("\n");
            }
            this.errorStack = sb.toString();
        }
    }

    public void addCountriesProcessed(int count) {
        this.countriesProcessed = (this.countriesProcessed != null ? this.countriesProcessed : 0) + count;
    }

    public void addStatisticsSaved(int count) {
        this.statisticsSaved = (this.statisticsSaved != null ? this.statisticsSaved : 0) + count;
    }

    public void addFetchError(String countryCode, String feedType, String error) {
        if (fetchErrors == null) fetchErrors = new ArrayList<>();
        fetchErrors.add(new FetchFailure(countryCode, feedType, error));
    }

    public void buildFetchDetailsJson() {
        if (fetchErrors == null || fetchErrors.isEmpty()) {
            this.fetchDetails = null;
            return;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < fetchErrors.size(); i++) {
            FetchFailure f = fetchErrors.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"countryCode\":\"").append(escape(f.countryCode()))
              .append("\",\"feedType\":\"").append(escape(f.feedType()))
              .append("\",\"error\":\"").append(escape(f.error()))
              .append("\"}");
        }
        sb.append("]");
        this.fetchDetails = sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
