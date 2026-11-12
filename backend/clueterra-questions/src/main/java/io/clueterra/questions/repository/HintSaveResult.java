package io.clueterra.questions.repository;

import io.clueterra.questions.entity.Hint;

public record HintSaveResult(Hint hint, HintUpsertAction action) {

    public boolean isNew() {
        return action == HintUpsertAction.INSERTED
                || action == HintUpsertAction.REPLACED;
    }

    public boolean isUpdated() {
        return action == HintUpsertAction.SOFT_UPDATED;
    }
}
