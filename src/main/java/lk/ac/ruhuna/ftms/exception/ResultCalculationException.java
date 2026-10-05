package lk.ac.ruhuna.ftms.exception;

/** An actionable result-data or configuration validation error. */
public class ResultCalculationException extends RuntimeException {
    public ResultCalculationException(String message) {
        super(message);
    }

    public ResultCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
