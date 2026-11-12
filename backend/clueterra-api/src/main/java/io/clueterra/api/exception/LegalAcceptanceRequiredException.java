package io.clueterra.api.exception;

public class LegalAcceptanceRequiredException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "You must accept the current Terms of Service and acknowledge the current Privacy Policy before continuing.";
    private static final String ERROR_CODE = "LEGAL_ACCEPTANCE_REQUIRED";

    public LegalAcceptanceRequiredException() {
        super(DEFAULT_MESSAGE);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
