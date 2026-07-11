package com.zalamtech.diama.domain.kyc;

import com.zalamtech.diama.common.dto.AdminKycReviewDto;
import com.zalamtech.diama.common.dto.KycDocumentDto;
import com.zalamtech.diama.common.dto.KycInitDto;
import com.zalamtech.diama.common.dto.KycStatusDto;
import com.zalamtech.diama.common.dto.SumsubWebhookPayload;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.AuditService;
import com.zalamtech.diama.domain.audit.NotificationService;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestration du parcours KYC (Sumsub) : initialisation, réception des
 * verdicts (webhook ou revue manuelle), et montée de niveau.
 *
 * <p>La propagation vers Keycloak ({@code kyc_level}) et le provisionnement
 * Swan (niveau 2+) ne sont <b>pas</b> déclenchés directement ici : chaque
 * mutation de {@code kyc_profiles} qui change {@code kyc_level} ou
 * {@code verification_status} déclenche le trigger SQL
 * {@code sync_kyc_level_to_keycloak}, qui enfile un événement
 * {@code KYC_LEVEL_CHANGED} dans {@code kafka_outbox} (topic {@code kyc-events})
 * — {@code KycEventConsumer} (module {@code diama-infra}) est le seul point
 * qui agit dessus, évitant tout double déclenchement.</p>
 */
@Slf4j
@ApplicationScoped
public class KycService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    @Inject
    UserRepository users;

    @Inject
    KycProfileRepository kycProfiles;

    @Inject
    KycDocumentRepository kycDocuments;

    @Inject
    SumsubGateway sumsub;

    @Inject
    NotificationService notifications;

    @Inject
    AuditService audit;

    // ---------------------------------------------------------------------
    //  Utilisateur
    // ---------------------------------------------------------------------

    /** Initialise (ou reprend) le parcours KYC : crée l'applicant Sumsub si besoin, renvoie un jeton SDK. */
    @Transactional
    public KycInitDto initKyc(UUID userId) {
        final User user = users.findByIdOptional(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        KycProfile profile = kycProfiles.findByUserId(userId).orElse(null);
        final int targetLevel = profile == null ? 1 : Math.min(profile.getKycLevel() + 1, KycLevels.MAX_LEVEL);
        final String levelName = KycLevels.sumsubLevelName(targetLevel);

        if (profile == null) {
            profile = KycProfile.builder()
                    .userId(userId)
                    .kycLevel((short) 0)
                    .verificationStatus(KycVerificationStatus.NOT_STARTED)
                    .build();
            kycProfiles.persist(profile);
        }

        if (profile.getSumsubApplicantId() == null) {
            final SumsubApplicant applicant = sumsub.createApplicant(userId.toString(), levelName);
            profile.setSumsubApplicantId(applicant.applicantId());
        }
        if (profile.getVerificationStatus() == KycVerificationStatus.NOT_STARTED
                || profile.getVerificationStatus() == KycVerificationStatus.REJECTED) {
            profile.setVerificationStatus(KycVerificationStatus.PENDING);
        }

        final String token = sumsub.generateAccessToken(profile.getSumsubApplicantId());
        log.info("Parcours KYC initialisé : user={} applicant={} niveau visé={}", userId, profile.getSumsubApplicantId(), targetLevel);

        return new KycInitDto(
                profile.getSumsubApplicantId(),
                token,
                levelName,
                OffsetDateTime.now(ZoneOffset.UTC).plus(TOKEN_TTL));
    }

    /** Statut KYC courant de l'utilisateur (niveau 0 / NOT_STARTED si le parcours n'a jamais démarré). */
    public KycStatusDto getKycStatus(UUID userId) {
        return kycProfiles.findByUserId(userId)
                .map(KycService::toStatusDto)
                .orElseGet(() -> new KycStatusDto(0, KycVerificationStatus.NOT_STARTED.name(),
                        KycLevels.monthlyLimit(1), java.math.BigDecimal.ZERO, KycLevels.monthlyLimit(1), null, null));
    }

    // ---------------------------------------------------------------------
    //  Webhook Sumsub
    // ---------------------------------------------------------------------

    /** Traite un webhook Sumsub déjà authentifié (signature vérifiée en amont). */
    @Transactional
    public void handleSumsubWebhook(SumsubWebhookPayload payload) {
        if (!"applicantReviewed".equals(payload.type())) {
            log.debug("Webhook Sumsub ignoré (type {})", payload.type());
            return;
        }

        final KycProfile profile = kycProfiles.findBySumsubApplicantId(payload.applicantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil KYC introuvable pour applicant Sumsub : " + payload.applicantId()));

        final SumsubWebhookPayload.ReviewResult review = payload.reviewResult();
        if (review == null) {
            log.warn("Webhook applicantReviewed sans reviewResult (applicant={})", payload.applicantId());
            return;
        }

        if ("GREEN".equalsIgnoreCase(review.reviewAnswer())) {
            final int newLevel = Math.min(profile.getKycLevel() + 1, KycLevels.MAX_LEVEL);
            upgradeKycLevel(profile.getUserId(), newLevel, null);
        } else if ("RED".equalsIgnoreCase(review.reviewAnswer())) {
            setRejected(profile, formatRejection(review), null);
        } else {
            log.info("Webhook applicantReviewed sans verdict exploitable (applicant={}, reviewAnswer={})",
                    payload.applicantId(), review.reviewAnswer());
        }
    }

    // ---------------------------------------------------------------------
    //  Montée de niveau / revue manuelle
    // ---------------------------------------------------------------------

    /** Fait passer l'utilisateur au niveau KYC indiqué (Sumsub GREEN ou revue manuelle approuvée). */
    @Transactional
    public void upgradeKycLevel(UUID userId, int newLevel) {
        upgradeKycLevel(userId, newLevel, null);
    }

    /**
     * Variante interne portant l'agent backoffice à l'origine (revue manuelle),
     * {@code null} pour une montée de niveau déclenchée par Sumsub. Appelée
     * uniquement en interne (self-invocation) : pas d'intercepteur transactionnel
     * propre nécessaire, elle rejoint la transaction déjà ouverte par
     * l'appelant ({@link #upgradeKycLevel(UUID, int)} ou {@link #manualReview}).
     */
    private void upgradeKycLevel(UUID userId, int newLevel, UUID adminId) {
        final KycProfile profile = kycProfiles.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil KYC", userId));

        final KycVerificationStatus before = profile.getVerificationStatus();
        final short beforeLevel = profile.getKycLevel();

        profile.setKycLevel((short) newLevel);
        profile.setMonthlyLimit(KycLevels.monthlyLimit(newLevel));
        profile.setVerificationStatus(KycVerificationStatus.APPROVED);
        profile.setLevelGrantedAt(OffsetDateTime.now(ZoneOffset.UTC));
        profile.setRejectionReason(null);
        profile.setReviewedBy(adminId);
        // La mutation ci-dessus déclenche trg_kyc_sync_keycloak (kafka_outbox → topic
        // kyc-events) : c'est KycEventConsumer, pas ce service, qui propage vers
        // Keycloak et provisionne le compte Swan (niveau 2+).

        audit.log(null, userId, adminId, "KYC_LEVEL_CHANGED", "KYC", profile.getId(),
                Map.of("kycLevel", beforeLevel, "verificationStatus", before.name()),
                Map.of("kycLevel", newLevel, "verificationStatus", KycVerificationStatus.APPROVED.name()));

        notifications.send(userId, null, "KYC_APPROVED", Map.of("kycLevel", newLevel));

        log.info("Niveau KYC relevé : user={} {} → {}", userId, beforeLevel, newLevel);
    }

    /** Revue manuelle backoffice d'un dossier KYC. */
    @Transactional
    public void manualReview(UUID kycProfileId, boolean approved, String reason, UUID adminId) {
        final KycProfile profile = kycProfiles.findByIdOptional(kycProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil KYC", kycProfileId));

        if (approved) {
            final int newLevel = Math.min(profile.getKycLevel() + 1, KycLevels.MAX_LEVEL);
            upgradeKycLevel(profile.getUserId(), newLevel, adminId);
        } else {
            setRejected(profile, reason, adminId);
        }
    }

    // ---------------------------------------------------------------------
    //  Backoffice
    // ---------------------------------------------------------------------

    /** Dossiers en attente de revue manuelle (résumé, sans documents). */
    public List<AdminKycReviewDto> getPendingReview() {
        return kycProfiles.findPendingReview().stream()
                .map(profile -> toReviewDto(profile, findUser(profile.getUserId()), List.of()))
                .toList();
    }

    /** Dossier KYC complet d'un utilisateur, documents resynchronisés depuis Sumsub (best-effort). */
    @Transactional
    public AdminKycReviewDto getFullDossier(UUID userId) {
        final KycProfile profile = kycProfiles.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil KYC", userId));
        final User user = findUser(userId);
        return toReviewDto(profile, user, syncDocuments(profile));
    }

    // ---------------------------------------------------------------------
    //  Aides internes
    // ---------------------------------------------------------------------

    private void setRejected(KycProfile profile, String reason, UUID adminId) {
        profile.setVerificationStatus(KycVerificationStatus.REJECTED);
        profile.setRejectionReason(reason);
        profile.setReviewedBy(adminId);
        // Mutation → trg_kyc_sync_keycloak (verification_status a changé, kyc_level non modifié :
        // KycEventConsumer ignore ce cas, aucun statut Keycloak/Swan à propager sur un rejet).

        audit.log(null, profile.getUserId(), adminId, "KYC_REJECTED", "KYC", profile.getId(), null,
                Map.of("reason", reason != null ? reason : ""));
        notifications.send(profile.getUserId(), null, "KYC_REJECTED", Map.of("reason", reason != null ? reason : ""));

        log.info("Dossier KYC rejeté : profile={} raison={}", profile.getId(), reason);
    }

    /**
     * Récupère les documents Sumsub et les upserte localement ; best-effort (ne
     * fait jamais échouer l'appelant). Appelée uniquement en interne depuis
     * {@link #getFullDossier} : pas de {@code @Transactional} propre — un appel
     * interne (self-invocation) contournerait de toute façon le proxy CDI qui
     * porte l'intercepteur transactionnel, elle rejoint donc la transaction de
     * l'appelant.
     */
    private List<KycDocumentDto> syncDocuments(KycProfile profile) {
        if (profile.getSumsubApplicantId() == null) {
            return toDocumentDtos(kycDocuments.findByKycProfileId(profile.getId()));
        }
        try {
            for (SumsubDocument doc : sumsub.getApplicantDocuments(profile.getSumsubApplicantId())) {
                upsertDocument(profile.getId(), doc);
            }
        } catch (RuntimeException e) {
            log.warn("Synchronisation des documents Sumsub impossible pour applicant={}", profile.getSumsubApplicantId(), e);
        }
        return toDocumentDtos(kycDocuments.findByKycProfileId(profile.getId()));
    }

    private void upsertDocument(UUID kycProfileId, SumsubDocument doc) {
        if (doc.sumsubDocId() == null) {
            return;
        }
        final KycDocument document = kycDocuments.findByKycProfileIdAndSumsubDocId(kycProfileId, doc.sumsubDocId())
                .orElseGet(() -> {
                    final KycDocument created = KycDocument.builder()
                            .kycProfileId(kycProfileId)
                            .sumsubDocId(doc.sumsubDocId())
                            .docType(mapDocType(doc.idDocType()))
                            .status(DocStatus.PENDING)
                            .build();
                    kycDocuments.persist(created);
                    return created;
                });
        document.setStatus(mapDocStatus(doc.status()));
    }

    private static DocType mapDocType(String sumsubType) {
        if (sumsubType == null) {
            return DocType.NATIONAL_ID;
        }
        return switch (sumsubType.toUpperCase(java.util.Locale.ROOT)) {
            case "PASSPORT" -> DocType.PASSPORT;
            case "RESIDENCE_PERMIT" -> DocType.RESIDENCE_PERMIT;
            case "UTILITY_BILL", "PROOF_OF_ADDRESS" -> DocType.PROOF_OF_ADDRESS;
            case "SELFIE" -> DocType.SELFIE;
            case "INCOME_SOURCE", "SOURCE_OF_FUNDS" -> DocType.SOURCE_OF_FUNDS;
            default -> DocType.NATIONAL_ID;
        };
    }

    private static DocStatus mapDocStatus(String sumsubStatus) {
        if (sumsubStatus == null) {
            return DocStatus.PENDING;
        }
        return switch (sumsubStatus.toUpperCase(java.util.Locale.ROOT)) {
            case "COMPLETED", "APPROVED" -> DocStatus.VERIFIED;
            case "REJECTED" -> DocStatus.REJECTED;
            default -> DocStatus.PENDING;
        };
    }

    private User findUser(UUID userId) {
        return users.findByIdOptional(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
    }

    private static String formatRejection(SumsubWebhookPayload.ReviewResult review) {
        if (review.moderationComment() != null && !review.moderationComment().isBlank()) {
            return review.moderationComment();
        }
        if (review.rejectLabels() != null && !review.rejectLabels().isEmpty()) {
            return String.join(", ", review.rejectLabels());
        }
        return "Vérification Sumsub rejetée";
    }

    private static KycStatusDto toStatusDto(KycProfile p) {
        final java.math.BigDecimal limit = p.getMonthlyLimit();
        final java.math.BigDecimal used = p.getMonthlyUsed();
        return new KycStatusDto(
                p.getKycLevel(),
                p.getVerificationStatus().name(),
                limit,
                used,
                limit.subtract(used),
                p.getRejectionReason(),
                p.getLevelGrantedAt());
    }

    private static AdminKycReviewDto toReviewDto(KycProfile p, User u, List<KycDocumentDto> documents) {
        return new AdminKycReviewDto(
                p.getId(),
                p.getUserId(),
                u.getEmail(),
                u.getFirstName() + " " + u.getLastName(),
                p.getKycLevel(),
                p.getVerificationStatus().name(),
                p.getRiskCategory(),
                p.getSumsubApplicantId(),
                p.getRejectionReason(),
                p.getMonthlyLimit(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                documents);
    }

    private static List<KycDocumentDto> toDocumentDtos(List<KycDocument> docs) {
        return docs.stream()
                .map(d -> new KycDocumentDto(d.getId(), d.getDocType().name(), d.getStatus().name(),
                        d.getRejectionReason(), d.getVerifiedAt()))
                .toList();
    }
}
