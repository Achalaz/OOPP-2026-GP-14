package lk.ac.ruhuna.ftms.exception;

/** Expected rejection of an academic edit that would change an already published result. */
public final class PublishedResultProtectedException extends IllegalStateException {
    public PublishedResultProtectedException() {
        super("Published academic results are protected. A formal correction policy is required.");
    }
}
