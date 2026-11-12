package io.clueterra.questions.clients.common;

@FunctionalInterface
public interface FetchProgressListener {
    void onProgress(Integer current, Integer total, String message);

    FetchProgressListener NO_OP = (current, total, message) -> {
    };

    static FetchProgressListener safe(FetchProgressListener listener) {
        return listener != null ? listener : NO_OP;
    }
}
