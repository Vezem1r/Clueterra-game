package io.clueterra.api.exception;

public class LegalVersionMismatchException extends RuntimeException {

    private static final String DEFAULT_MESSAGE =
            "Legal terms have changed. Please review the current Terms of Service and Privacy Policy and try again.";
    private static final String ERROR_CODE = "LEGAL_VERSION_MISMATCH";

    public LegalVersionMismatchException() {
        super(DEFAULT_MESSAGE);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
