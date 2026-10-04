package cl.duoc.backendiii.events;

public final class TopicNames {
    private TopicNames() {
    }

    public static final String WITHDRAWAL_REQUESTED = "withdrawal.requested";
    public static final String FUNDS_RESERVED = "funds.reserved";
    public static final String FUNDS_REJECTED = "funds.rejected";
    public static final String RISK_APPROVED = "risk.approved";
    public static final String RISK_REJECTED = "risk.rejected";
    public static final String WITHDRAWAL_CONFIRMED = "withdrawal.confirmed";
    public static final String WITHDRAWAL_CANCELLED = "withdrawal.cancelled";
    public static final String FUNDS_RELEASE_REQUESTED = "funds.release-requested";
    public static final String FUNDS_RELEASED = "funds.released";
}
