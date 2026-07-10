package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.exchange.ThunesPayerRepository;
import com.zalamtech.diama.domain.transaction.RfiInfo;
import com.zalamtech.diama.domain.transaction.ThunesTransferGateway;
import com.zalamtech.diama.domain.transaction.ThunesTransferResult;
import com.zalamtech.diama.domain.transaction.Transaction;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserBeneficiary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptateur Thunes v2 : orchestre le client REST bas niveau {@link ThunesApiClient}
 * et traduit ses DTO de transport en types du domaine. Implémente le port
 * {@link ThunesTransferGateway} (architecture hexagonale) ; expose en outre les
 * opérations bas niveau (devis, confirmation, RFI) utilisées en interne.
 *
 * <p>Motifs/valeurs par défaut ({@code purpose_of_remittance}, {@code source_of_funds},
 * {@code transaction_type}) sont simplifiés pour le corridor Europe → Afrique et
 * l'environnement sandbox ; à affiner selon les exigences compliance réelles.</p>
 */
@Slf4j
@ApplicationScoped
public class ThunesClient implements ThunesTransferGateway {

    private static final String DEFAULT_SERVICE_TYPE = "MobileWallet";
    private static final String QUOTATION_MODE = "SOURCE_AMOUNT";
    private static final String TRANSACTION_TYPE = "C2C";
    private static final String PURPOSE_OF_REMITTANCE = "FAMILY_SUPPORT";
    private static final String SOURCE_OF_FUNDS = "SALARY";

    @Inject
    @RestClient
    ThunesApiClient api;

    @Inject
    ThunesPayerRepository payers;

    @Inject
    ObjectMapper objectMapper;

    // -----------------------------------------------------------------
    //  Port ThunesTransferGateway (façade domaine)
    // -----------------------------------------------------------------

    @Override
    public List<ThunesPayer> getPayers(String countryIso) {
        return api.getPayers(countryIso).stream()
                .map(ThunesClient::toEntity)
                .toList();
    }

    @Override
    @Transactional
    public List<ThunesPayer> syncPayers() {
        final List<ThunesPayerResponse> remote = api.getPayers(null);
        final List<ThunesPayer> result = new ArrayList<>(remote.size());
        for (ThunesPayerResponse r : remote) {
            final ThunesPayer existing = payers.findByIdOptional(r.id()).orElse(null);
            final boolean isNew = existing == null;
            final ThunesPayer entity = isNew ? new ThunesPayer() : existing;
            applyResponse(entity, r);
            if (isNew) {
                payers.persist(entity);
            }
            result.add(entity);
        }
        log.info("Synchronisation des payers Thunes : {} enregistrés", result.size());
        return result;
    }

    @Override
    public ThunesTransferResult confirmTransfer(Transaction tx, User sender, UserBeneficiary beneficiary) {
        final ThunesQuotation quotation = createQuotation(buildQuotationRequest(tx, sender, beneficiary));
        if (quotation == null || quotation.id() == null) {
            throw new IllegalStateException("Devis Thunes sans identifiant pour la transaction " + tx.getId());
        }

        final ThunesTransaction thunesTx =
                confirmTransaction(quotation.id(), buildTransactionRequest(tx, sender, beneficiary));
        if (thunesTx == null || thunesTx.id() == null) {
            throw new IllegalStateException("Transaction Thunes sans identifiant (devis " + quotation.id() + ")");
        }

        log.info("Transfert Thunes confirmé : tx interne={} → thunes={} (devis {})",
                tx.getId(), thunesTx.id(), quotation.id());
        return new ThunesTransferResult(quotation.id(), thunesTx.id(), thunesTx.status());
    }

    @Override
    public ThunesTransferResult fetchTransaction(Long thunesTransactionId) {
        final ThunesTransaction t = getTransaction(thunesTransactionId);
        if (t == null || t.id() == null) {
            throw new IllegalStateException("Transaction Thunes introuvable : " + thunesTransactionId);
        }
        return new ThunesTransferResult(null, t.id(), t.status());
    }

    @Override
    public void uploadRFIDocument(Long thunesRfiId, byte[] content, String mimeType) {
        api.uploadRfiDocument(thunesRfiId, mimeType, content);
        log.info("Document RFI Thunes téléversé : rfi={} ({} octets, {})", thunesRfiId, content.length, mimeType);
    }

    @Override
    public void submitRFIInfo(Long thunesRfiId, Map<String, String> info) {
        api.submitRfiInfo(thunesRfiId, info);
        log.info("Informations RFI Thunes soumises : rfi={} ({} champs)", thunesRfiId, info.size());
    }

    @Override
    public Optional<RfiInfo> fetchOpenRFI(Long thunesTransactionId) {
        final List<ThunesRFIPayload> rfis = api.getRfis(thunesTransactionId);
        return rfis.stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.status()))
                .findFirst()
                .or(() -> rfis.stream().findFirst())
                .map(this::toRfiInfo);
    }

    private RfiInfo toRfiInfo(ThunesRFIPayload payload) {
        return new RfiInfo(
                payload.id(),
                writeAsJson(payload.requestedInfo()),
                payload.requestedDocs(),
                payload.description(),
                parseExpiration(payload.expirationDate()));
    }

    private String writeAsJson(JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            log.warn("Sérialisation des informations RFI demandées impossible", e);
            return null;
        }
    }

    private static OffsetDateTime parseExpiration(String expirationDate) {
        if (expirationDate == null || expirationDate.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(expirationDate);
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("Échéance RFI Thunes illisible : {}", expirationDate);
            return null;
        }
    }

    // -----------------------------------------------------------------
    //  Opérations bas niveau (transport Thunes)
    // -----------------------------------------------------------------

    public ThunesQuotation createQuotation(ThunesQuotationRequest request) {
        return api.createQuotation(request);
    }

    public ThunesTransaction confirmTransaction(Long quotationId, ThunesTransactionRequest request) {
        return api.confirmTransaction(quotationId, request);
    }

    public ThunesTransaction getTransaction(Long id) {
        return api.getTransaction(id);
    }

    // -----------------------------------------------------------------
    //  Construction des requêtes Thunes
    // -----------------------------------------------------------------

    private static ThunesQuotationRequest buildQuotationRequest(Transaction tx, User sender, UserBeneficiary beneficiary) {
        return new ThunesQuotationRequest(
                tx.getThunesExternalId(),
                tx.getThunesPayerId(),
                QUOTATION_MODE,
                TRANSACTION_TYPE,
                new ThunesQuotationRequest.Source(
                        tx.getSourceCurrency(), sender.getCountryOfResidence(), tx.getSourceAmount()),
                new ThunesQuotationRequest.Destination(
                        tx.getDestCurrency(), beneficiary.getCountryIso()));
    }

    private static ThunesTransactionRequest buildTransactionRequest(Transaction tx, User sender, UserBeneficiary beneficiary) {
        return new ThunesTransactionRequest(
                tx.getThunesExternalId(),
                buildSender(sender),
                buildBeneficiary(beneficiary),
                buildCreditParty(beneficiary),
                PURPOSE_OF_REMITTANCE,
                SOURCE_OF_FUNDS);
    }

    private static ThunesSender buildSender(User sender) {
        return new ThunesSender(
                sender.getLastName(),
                sender.getFirstName(),
                sender.getDateOfBirth() != null ? sender.getDateOfBirth().toString() : null,
                sender.getCountryOfResidence(),
                sender.getNationality(),
                sender.getPhone(),
                sender.getEmail());
    }

    private static ThunesBeneficiary buildBeneficiary(UserBeneficiary beneficiary) {
        return new ThunesBeneficiary(
                beneficiary.getLastName(),
                beneficiary.getFirstName(),
                beneficiary.getCountryIso(),
                beneficiary.getPhoneMsisdn(),
                null);
    }

    private static ThunesCreditPartyIdentifier buildCreditParty(UserBeneficiary beneficiary) {
        if ("BankAccount".equals(beneficiary.getPayerService())) {
            return new ThunesCreditPartyIdentifier(
                    null, beneficiary.getBankAccountNo(), beneficiary.getSwiftBic(), null);
        }
        // MobileWallet / CashPickup : identification par MSISDN.
        return new ThunesCreditPartyIdentifier(beneficiary.getPhoneMsisdn(), null, null, null);
    }

    // -----------------------------------------------------------------
    //  Mapping payer Thunes → entité domaine
    // -----------------------------------------------------------------

    private static ThunesPayer toEntity(ThunesPayerResponse r) {
        final ThunesPayer payer = new ThunesPayer();
        applyResponse(payer, r);
        return payer;
    }

    private static void applyResponse(ThunesPayer entity, ThunesPayerResponse r) {
        entity.setId(r.id());
        entity.setName(r.name());
        entity.setCountryIso(r.countryIsoCode());
        entity.setCurrency(r.currency());
        entity.setServiceType(r.service() != null ? r.service() : DEFAULT_SERVICE_TYPE);
        entity.setMinAmount(r.minAmount());
        entity.setMaxAmount(r.maxAmount());
        if (r.precision() != null) {
            entity.setPrecision(r.precision());
        }
        entity.setIncrement(r.increment());
        entity.setIsActive(Boolean.TRUE);
    }
}
