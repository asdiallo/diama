package com.zalamtech.diama.domain.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.common.dto.CreateTransferRequest;
import com.zalamtech.diama.common.dto.LimitCheckResult;
import com.zalamtech.diama.common.dto.Page;
import com.zalamtech.diama.common.dto.QuotationDto;
import com.zalamtech.diama.common.dto.TransferDto;
import com.zalamtech.diama.common.dto.TransferStatusDto;
import com.zalamtech.diama.common.exception.InvalidTransferStateException;
import com.zalamtech.diama.common.exception.LimitExceededException;
import com.zalamtech.diama.common.exception.QuotationExpiredException;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.exchange.ThunesPayerRepository;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserBeneficiary;
import com.zalamtech.diama.domain.user.UserBeneficiaryRepository;
import com.zalamtech.diama.domain.user.UserRepository;
import com.zalamtech.diama.domain.user.UserService;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Orchestration du cycle de vie d'un transfert Europe → Afrique.
 *
 * <ul>
 *   <li><b>Initiation</b> : consomme un devis verrouillé (Redis), revérifie le
 *       plafond KYC, crée la transaction {@code INITIATED} et publie
 *       {@code transfer-initiated}.</li>
 *   <li><b>Confirmation</b> : délègue le payout à Thunes (port
 *       {@link ThunesTransferGateway}), enregistre les références Thunes et fait
 *       transiter la FSM vers {@code CONFIRMED}.</li>
 *   <li><b>Consultation / annulation</b> : toujours restreintes au propriétaire.</li>
 * </ul>
 *
 * <p>Les {@code userId} sont les PK internes {@code users.id} (résolution
 * keycloak_id → users.id faite en amont par la couche API).</p>
 */
@Slf4j
@ApplicationScoped
public class TransferService {

    private static final String EVENT_INITIATED = "transfer-initiated";

    @Inject
    TransactionRepository transactions;

    @Inject
    UserRepository users;

    @Inject
    UserBeneficiaryRepository beneficiaries;

    @Inject
    ThunesPayerRepository payers;

    @Inject
    UserService userService;

    @Inject
    TransactionFSM fsm;

    @Inject
    ThunesTransferGateway thunes;

    @Inject
    TransferEventPublisher events;

    @Inject
    RedisDataSource redis;

    @Inject
    ObjectMapper mapper;

    private ValueCommands<String, String> cache;

    @PostConstruct
    void init() {
        this.cache = redis.value(String.class);
    }

    // ---------------------------------------------------------------------
    //  Initiation
    // ---------------------------------------------------------------------

    /**
     * Initie un transfert à partir d'un devis verrouillé.
     *
     * @throws QuotationExpiredException     devis expiré / déjà consommé
     * @throws LimitExceededException        plafond mensuel KYC dépassé
     * @throws ResourceNotFoundException     bénéficiaire inconnu
     * @throws InvalidTransferStateException bénéficiaire désactivé
     */
    @Transactional
    public TransferDto initiateTransfer(UUID userId, CreateTransferRequest req) {
        // Consommation atomique du devis (get + delete) : un devis ne sert qu'une fois.
        final QuotationDto quotation = consumeQuotation(userId, req.quotationId());

        final LimitCheckResult limit = userService.checkMonthlyLimit(userId, quotation.sourceAmount());
        if (!limit.allowed()) {
            throw new LimitExceededException(
                    "Plafond mensuel dépassé : disponible %s, demandé %s (KYC niveau %d)"
                            .formatted(limit.available(), quotation.sourceAmount(), limit.kycLevel()));
        }

        final UserBeneficiary beneficiary = beneficiaries.findByIdAndUserId(req.beneficiaryId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Bénéficiaire", req.beneficiaryId()));
        if (Boolean.FALSE.equals(beneficiary.getIsActive())) {
            throw new InvalidTransferStateException("Bénéficiaire désactivé : " + req.beneficiaryId());
        }

        final Transaction tx = Transaction.builder()
                .userId(userId)
                .beneficiaryId(beneficiary.getId())
                .status(TxStatus.INITIATED)
                .sourceCurrency(quotation.sourceCurrency())
                .sourceAmount(quotation.sourceAmount())
                .destCurrency(quotation.destCurrency())
                .destAmount(quotation.destAmount())
                .exchangeRate(quotation.clientRate())
                .feeAmount(quotation.feeAmount())
                .feeCurrency(quotation.sourceCurrency())
                .thunesPayerId(beneficiary.getThunesPayerId())
                .thunesExternalId(UUID.randomUUID().toString())
                .build();
        transactions.persist(tx);

        recordInitialHistory(tx);
        events.publish(tx, EVENT_INITIATED);

        log.info("Transfert initié : tx={} user={} montant={} {}",
                tx.getId(), userId, quotation.sourceAmount(), quotation.sourceCurrency());
        return toDto(tx);
    }

    // ---------------------------------------------------------------------
    //  Confirmation → Thunes
    // ---------------------------------------------------------------------

    /**
     * Envoie le payout à Thunes puis fait transiter la transaction vers
     * {@code CONFIRMED}. Précondition : statut {@code AML_CLEARED}.
     */
    @Transactional
    public void confirmAndSendToThunes(UUID transactionId) {
        final Transaction tx = transactions.findByIdOptional(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));

        if (tx.getStatus() != TxStatus.AML_CLEARED) {
            throw new InvalidTransferStateException(
                    "Confirmation impossible depuis le statut %s (AML_CLEARED requis)".formatted(tx.getStatus()));
        }

        final User sender = users.findByIdOptional(tx.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", tx.getUserId()));
        final UserBeneficiary beneficiary = beneficiaries.findByIdAndUserId(tx.getBeneficiaryId(), tx.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Bénéficiaire", tx.getBeneficiaryId()));

        final ThunesTransferResult result = thunes.confirmTransfer(tx, sender, beneficiary);
        tx.setThunesQuotationId(result.thunesQuotationId());
        tx.setThunesTransactionId(result.thunesTransactionId());

        fsm.transition(transactionId, TxStatus.CONFIRMED,
                "Confirmé auprès de Thunes (tx %s)".formatted(result.thunesTransactionId()), null);
    }

    // ---------------------------------------------------------------------
    //  Consultation / annulation
    // ---------------------------------------------------------------------

    @Transactional
    public TransferDto getTransfer(UUID transactionId, UUID userId) {
        return toDto(requireOwned(transactionId, userId));
    }

    @Transactional
    public Page<TransferDto> listTransfers(UUID userId, int page, int size) {
        final List<TransferDto> content = transactions.findByUserIdPaginated(userId, page, size).stream()
                .map(this::toDto)
                .toList();
        final long total = transactions.count("userId", userId);
        return Page.of(content, page, size, total);
    }

    /** Annulation possible uniquement tant que le transfert est {@code INITIATED}. */
    @Transactional
    public void cancelTransfer(UUID transactionId, UUID userId) {
        final Transaction tx = requireOwned(transactionId, userId);
        if (tx.getStatus() != TxStatus.INITIATED) {
            throw new InvalidTransferStateException(
                    "Annulation possible uniquement au statut INITIATED (statut courant : %s)".formatted(tx.getStatus()));
        }
        fsm.transition(transactionId, TxStatus.CANCELLED, "Annulé par l'utilisateur", null);
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------

    private Transaction requireOwned(UUID transactionId, UUID userId) {
        return transactions.findByIdOptional(transactionId)
                .filter(tx -> userId.equals(tx.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
    }

    private QuotationDto consumeQuotation(UUID userId, UUID quotationId) {
        final String key = "quot:%s:%s".formatted(userId, quotationId);
        final String json = cache.getdel(key);
        if (json == null) {
            throw new QuotationExpiredException("Devis expiré ou introuvable : " + quotationId);
        }
        try {
            return mapper.readValue(json, QuotationDto.class);
        } catch (JsonProcessingException e) {
            throw new QuotationExpiredException("Devis illisible : " + quotationId);
        }
    }

    private void recordInitialHistory(Transaction tx) {
        TransactionStatusHistory.builder()
                .transactionId(tx.getId())
                .fromStatus(null)
                .toStatus(TxStatus.INITIATED)
                .triggeredBy("USER")
                .metadata("{\"reason\":\"Transfert initié\"}")
                .build()
                .persist();
    }

    private TransferDto toDto(Transaction tx) {
        final BigDecimal source = tx.getSourceAmount() != null ? tx.getSourceAmount() : BigDecimal.ZERO;
        final BigDecimal fee = tx.getFeeAmount() != null ? tx.getFeeAmount() : BigDecimal.ZERO;
        final String payerName = tx.getThunesPayerId() == null ? null
                : payers.findByIdOptional(tx.getThunesPayerId()).map(ThunesPayer::getName).orElse(null);

        final TransferStatusDto status = new TransferStatusDto(
                tx.getStatus() != null ? tx.getStatus().name() : null,
                tx.getFailureReason(),
                tx.getUpdatedAt(),
                tx.getCompletedAt());

        return new TransferDto(
                tx.getId(),
                status,
                tx.getSourceAmount(),
                tx.getSourceCurrency(),
                tx.getDestAmount(),
                tx.getDestCurrency(),
                tx.getExchangeRate(),
                tx.getFeeAmount(),
                tx.getFeeCurrency(),
                source.add(fee),
                payerName,
                tx.getBeneficiaryId(),
                tx.getThunesTransactionId(),
                tx.getCreatedAt());
    }
}
