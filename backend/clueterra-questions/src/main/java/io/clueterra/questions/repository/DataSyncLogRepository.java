package io.clueterra.questions.repository;

import io.clueterra.questions.entity.DataSyncLog;

public interface DataSyncLogRepository {

    DataSyncLog save(DataSyncLog syncLog);

    DataSyncLog update(DataSyncLog syncLog);
}
