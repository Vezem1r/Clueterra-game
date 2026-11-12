package io.clueterra.budget.exception;

import java.time.Duration;

public class MediaWikiMaxLagException extends ExternalSourceException {

    private final Duration suggestedDelay;

    public MediaWikiMaxLagException(String operation, String details, Duration suggestedDelay) {
        super(operation + " returned API error maxlag: " + details);
        this.suggestedDelay = suggestedDelay;
    }

    public Duration suggestedDelay() {
        return suggestedDelay;
    }
}
