package io.clueterra.questions.service.challenge;

public class ChallengeGenerationException extends RuntimeException {

    public ChallengeGenerationException(String message, Throwable firstFailure) {
        super(message, firstFailure);
    }
}
