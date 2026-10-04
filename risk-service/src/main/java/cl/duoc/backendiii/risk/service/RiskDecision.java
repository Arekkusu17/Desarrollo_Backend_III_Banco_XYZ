package cl.duoc.backendiii.risk.service;

public record RiskDecision(
        boolean approved,
        String authorizationCode,
        String reason
) {
    public static RiskDecision approved(String authorizationCode) {
        return new RiskDecision(true, authorizationCode, null);
    }

    public static RiskDecision rejected(String reason) {
        return new RiskDecision(false, null, reason);
    }
}
