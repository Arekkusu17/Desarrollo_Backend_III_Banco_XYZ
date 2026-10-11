package cl.duoc.backendiii.bankbatch.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FundReservationStore {
    private final ConcurrentHashMap<UUID, Reservation> reservationsByTransaction = new ConcurrentHashMap<>();

    public BigDecimal reservedAmountForAccount(long accountId) {
        return reservationsByTransaction.values().stream()
                .filter(reservation -> reservation.accountId() == accountId)
                .map(Reservation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void reserve(UUID transactionId, long accountId, BigDecimal amount) {
        reservationsByTransaction.put(transactionId, new Reservation(accountId, amount));
    }

    public Optional<Reservation> release(UUID transactionId) {
        return Optional.ofNullable(reservationsByTransaction.remove(transactionId));
    }

    public record Reservation(long accountId, BigDecimal amount) {
    }
}
