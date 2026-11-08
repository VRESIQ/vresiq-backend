package in.rithik.resumebuilderapi.service;

/** A safe-to-log summary of a provider error, without recipient or response-body data. */
public class EmailDeliveryException extends RuntimeException {
    public enum Outcome { REJECTED, UNKNOWN }

    private final String failureCode;
    private final Outcome outcome;
    private final boolean retryable;

    public EmailDeliveryException(String failureCode, Outcome outcome, boolean retryable) {
        super("Email provider operation failed");
        this.failureCode = failureCode;
        this.outcome = outcome;
        this.retryable = retryable;
    }

    public String getFailureCode() { return failureCode; }
    public Outcome getOutcome() { return outcome; }
    public boolean isRetryable() { return retryable; }
}
