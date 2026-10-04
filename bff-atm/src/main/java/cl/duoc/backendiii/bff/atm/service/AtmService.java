package cl.duoc.backendiii.bff.atm.service;

import cl.duoc.backendiii.bff.atm.client.CoreBankingClient;
import cl.duoc.backendiii.bff.atm.model.AtmBalanceResponse;
import cl.duoc.backendiii.bff.atm.model.WithdrawalRequest;
import cl.duoc.backendiii.bff.atm.model.WithdrawalStatus;
import cl.duoc.backendiii.bff.atm.model.WithdrawalTransaction;
import cl.duoc.backendiii.bff.atm.model.WithdrawalTransactionResponse;
import cl.duoc.backendiii.bff.atm.repository.WithdrawalTransactionRepository;
import cl.duoc.backendiii.bff.common.model.AccountBalance;
import cl.duoc.backendiii.events.TopicNames;
import cl.duoc.backendiii.events.WithdrawalRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AtmService {
    private static final Logger log = LoggerFactory.getLogger(AtmService.class);

    private final CoreBankingClient coreBankingClient;
    private final WithdrawalTransactionRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BigDecimal maxWithdrawalAmount;

    public AtmService(CoreBankingClient coreBankingClient,
                      WithdrawalTransactionRepository repository,
                      KafkaTemplate<String, Object> kafkaTemplate,
                      @Value("${atm.max-withdrawal-amount}") BigDecimal maxWithdrawalAmount) {
        this.coreBankingClient = coreBankingClient;
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.maxWithdrawalAmount = maxWithdrawalAmount;
    }

    public AtmBalanceResponse balance(long accountId) {
        AccountBalance balance = coreBankingClient.balance(accountId);
        return new AtmBalanceResponse("ATM", balance.accountId(), balance.availableBalance(), "CLP");
    }

    public WithdrawalTransactionResponse withdraw(long accountId, WithdrawalRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return rejected(accountId, request.amount(), "Monto de retiro invalido");
        }
        if (request.pin() == null || request.pin().length() != 4) {
            return rejected(accountId, request.amount(), "PIN invalido");
        }
        if (request.amount().compareTo(maxWithdrawalAmount) > 0) {
            return rejected(accountId, request.amount(), "Monto supera limite permitido para cajero");
        }

        UUID transactionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        WithdrawalTransaction transaction = repository.save(new WithdrawalTransaction(
                transactionId,
                eventId,
                accountId,
                request.amount(),
                Instant.now(),
                WithdrawalStatus.PENDING,
                "Retiro recibido y pendiente de validacion asincrona"
        ));

        publishWithdrawalRequested(transaction);
        return WithdrawalTransactionResponse.from(transaction);
    }

    public Optional<WithdrawalTransactionResponse> findTransaction(UUID transactionId) {
        return repository.findById(transactionId).map(WithdrawalTransactionResponse::from);
    }

    public List<WithdrawalTransactionResponse> transactions() {
        return repository.findAll().stream()
                .map(WithdrawalTransactionResponse::from)
                .toList();
    }

    public Optional<WithdrawalTransactionResponse> replayWithdrawalRequested(UUID transactionId) {
        Optional<WithdrawalTransaction> transaction = repository.findById(transactionId);
        transaction.ifPresent(this::publishWithdrawalRequested);
        return transaction.map(WithdrawalTransactionResponse::from);
    }

    private void publishWithdrawalRequested(WithdrawalTransaction transaction) {
        WithdrawalRequestedEvent event = new WithdrawalRequestedEvent(
                transaction.getWithdrawalRequestedEventId(),
                transaction.getTransactionId(),
                Instant.now(),
                transaction.getAccountId(),
                transaction.getAmount(),
                "ATM"
        );
        kafkaTemplate.send(TopicNames.WITHDRAWAL_REQUESTED, transaction.getTransactionId().toString(), event);
        log.info("[PRODUCER] WithdrawalRequested publicado transactionId={} eventId={}",
                transaction.getTransactionId(), event.eventId());
    }

    private WithdrawalTransactionResponse rejected(long accountId, BigDecimal amount, String message) {
        WithdrawalTransaction transaction = new WithdrawalTransaction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                accountId,
                amount,
                Instant.now(),
                WithdrawalStatus.CANCELLED,
                message
        );
        return WithdrawalTransactionResponse.from(transaction);
    }
}
