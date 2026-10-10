package com.zalamtech.diama.domain.kyc;

import com.zalamtech.diama.common.dto.AdminKycReviewDto;
import com.zalamtech.diama.common.dto.DiditWebhookPayload;
import com.zalamtech.diama.common.dto.KycDocumentDto;
import com.zalamtech.diama.common.dto.KycInitDto;
import com.zalamtech.diama.common.dto.KycStatusDto;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.AuditService;
import com.zalamtech.diama.domain.audit.NotificationService;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestration du parcours KYC (Didit) : initialisation, réception des
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

    private static final String WEBHOOK_STATUS_UPDATED = "status.updated";
    private static final String DIDIT_APPROVED = "Approved";
    private static final String DIDIT_DECLINED = "Declined";

    @Inject
    UserRepository users;

    @Inject
    KycProfileRepository kycProfiles;

    @Inject
    KycDocumentRepository kycDocuments;

    @Inject
    DiditGateway didit;

    @Inject
    KycWorkflows workflows;

    @Inject
    NotificationService notifications;

    @Inject
    AuditService audit;

    // ---------------------------------------------------------------------
    //  Utilisateur
    // ---------------------------------------------------------------------

    /**
     * Initialise (ou reprend) le parcours KYC : ouvre une session Didit sur le
     * workflow du niveau visé et renvoie de quoi la présenter côté mobile.
     *
     * <p>Chaque appel crée une nouvelle session (Didit ne réutilise pas une
     * session comme Sumsub réutilisait un applicant) ; seule la dernière est
     * rattachée au profil, les webhooks d'une session antérieure sont ignorés.</p>
     */
    @Transactional
    public KycInitDto initKyc(UUID userId) {
        final User user = users.findByIdOptional(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        KycProfile profile = kycProfiles.findByUserId(userId).orElse(null);
        final int targetLevel = profile == null ? 1 : Math.min(profile.getKycLevel() + 1, KycLevels.MAX_LEVEL);

        if (profile == null) {
            profile = KycProfile.builder()
                    .userId(userId)
                    .kycLevel((short) 0)
                    .verificationStatus(KycVerificationStatus.NOT_STARTED)
                    .build();
            kycProfiles.persist(profile);
        }

        final DiditSession session = didit.createSession(workflows.workflowId(targetLevel), userId.toString());
        profile.setDiditSessionId(session.sessionId());
        if (profile.getVerificationStatus() == KycVerificationStatus.NOT_STARTED
                || profile.getVerificationStatus() == KycVerificationStatus.REJECTED) {
            profile.setVerificationStatus(KycVerificationStatus.PENDING);
        }

        log.info("Parcours KYC initialisé : user={} session={} niveau visé={}", userId, session.sessionId(), targetLevel);

        return new KycInitDto(session.sessionId(), session.sessionToken(), session.url(), targetLevel);
    }

    /** Statut KYC courant de l'utilisateur (niveau 0 / NOT_STARTED si le parcours n'a jamais démarré). */
    public KycStatusDto getKycStatus(UUID userId) {
        return kycProfiles.findByUserId(userId)
                .map(KycService::toStatusDto)
                .orElseGet(() -> new KycStatusDto(0, KycVerificationStatus.NOT_STARTED.name(),
                        KycLevels.monthlyLimit(1), java.math.BigDecimal.ZERO, KycLevels.monthlyLimit(1), null, null));
    }

    // ---------------------------------------------------------------------
    //  Webhook Didit
    // ---------------------------------------------------------------------

    /**
     * Traite un webhook Didit déjà authentifié (signature vérifiée en amont).
     * Seuls les statuts finaux {@code Approved} / {@code Declined} d'un
     * {@code status.updated} agissent ; les statuts intermédiaires
     * ({@code In Review}, {@code In Progress}, …) laissent le profil en attente.
     */
    @Transactional
    public void handleDiditWebhook(DiditWebhookPayload payload) {
        if (!WEBHOOK_STATUS_UPDATED.equals(payload.webhookType())) {
            log.debug("Webhook Didit ignoré (type {})", payload.webhookType());
            return;
        }

        final KycProfile profile = kycProfiles.findByDiditSessionId(payload.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Profil KYC introuvable pour session Didit : " + payload.sessionId()));

        if (DIDIT_APPROVED.equals(payload.status())) {
            // Niveau porté par le workflow de la session ; à défaut, niveau suivant.
            final int newLevel = workflows.levelOf(payload.workflowId())
                    .orElse(Math.min(profile.getKycLevel() + 1, KycLevels.MAX_LEVEL));
            if (profile.getKycLevel() >= newLevel) {
                // Webhook rejoué (Didit renvoie sur 5xx/timeout) : déjà appliqué.
                log.info("Webhook Didit Approved déjà appliqué (session={}, niveau={})", payload.sessionId(), newLevel);
                return;
            }
            upgradeKycLevel(profile.getUserId(), newLevel, null);
        } else if (DIDIT_DECLINED.equals(payload.status())) {
            setRejected(profile, formatRejection(payload.decision()), null);
        } else {
            log.info("Webhook Didit sans verdict final (session={}, status={})", payload.sessionId(), payload.status());
        }
    }

    // ---------------------------------------------------------------------
    //  Montée de niveau / revue manuelle
    // ---------------------------------------------------------------------

    /** Fait passer l'utilisateur au niveau KYC indiqué (Didit Approved ou revue manuelle approuvée). */
    @Transactional
    public void upgradeKycLevel(UUID userId, int newLevel) {
        upgradeKycLevel(userId, newLevel, null);
    }

    /**
     * Variante interne portant l'agent backoffice à l'origine (revue manuelle),
     * {@code null} pour une montée de niveau déclenchée par Didit. Appelée
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

    /** Dossier KYC complet d'un utilisateur, documents resynchronisés depuis Didit (best-effort). */
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
     * Récupère les documents de la session Didit courante et les upserte
     * localement ; best-effort (ne fait jamais échouer l'appelant). Appelée
     * uniquement en interne depuis {@link #getFullDossier} : pas de
     * {@code @Transactional} propre — un appel interne (self-invocation)
     * contournerait de toute façon le proxy CDI qui porte l'intercepteur
     * transactionnel, elle rejoint donc la transaction de l'appelant.
     */
    private List<KycDocumentDto> syncDocuments(KycProfile profile) {
        final String sessionId = profile.getDiditSessionId();
        if (sessionId == null) {
            return toDocumentDtos(kycDocuments.findByKycProfileId(profile.getId()));
        }
        try {
            for (DiditDocument doc : didit.getDocuments(sessionId)) {
                upsertDocument(profile.getId(), sessionId, doc);
            }
        } catch (RuntimeException e) {
            log.warn("Synchronisation des documents Didit impossible pour session={}", sessionId, e);
        }
        return toDocumentDtos(kycDocuments.findByKycProfileId(profile.getId()));
    }

    private void upsertDocument(UUID kycProfileId, String sessionId, DiditDocument doc) {
        if (doc.nodeId() == null) {
            return;
        }
        // node_id n'est unique qu'au sein d'une session : identifiant composé.
        final String documentId = sessionId + ":" + doc.nodeId();
        final KycDocument document = kycDocuments.findByKycProfileIdAndDiditDocumentId(kycProfileId, documentId)
                .orElseGet(() -> {
                    final KycDocument created = KycDocument.builder()
                            .kycProfileId(kycProfileId)
                            .diditDocumentId(documentId)
                            .docType(mapDocType(doc.documentType()))
                            .status(DocStatus.PENDING)
                            .build();
                    kycDocuments.persist(created);
                    return created;
                });
        document.setStatus(mapDocStatus(doc.status()));
    }

    /** Types Didit ({@code Passport}, {@code Identity Card}, {@code Residence Permit}, {@code Driving License}, …). */
    private static DocType mapDocType(String diditType) {
        if (diditType == null) {
            return DocType.NATIONAL_ID;
        }
        return switch (diditType.toUpperCase(Locale.ROOT).replace(' ', '_')) {
            case "PASSPORT" -> DocType.PASSPORT;
            case "RESIDENCE_PERMIT" -> DocType.RESIDENCE_PERMIT;
            default -> DocType.NATIONAL_ID;
        };
    }

    private static DocStatus mapDocStatus(String diditStatus) {
        if (diditStatus == null) {
            return DocStatus.PENDING;
        }
        return switch (diditStatus) {
            case DIDIT_APPROVED -> DocStatus.VERIFIED;
            case DIDIT_DECLINED -> DocStatus.REJECTED;
            default -> DocStatus.PENDING;
        };
    }

    private User findUser(UUID userId) {
        return users.findByIdOptional(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
    }

    /** Motif de rejet : commentaire du réviseur Didit, sinon alertes documentaires, sinon libellé générique. */
    private static String formatRejection(DiditWebhookPayload.Decision decision) {
        if (decision != null) {
            if (decision.reviews() != null) {
                final String comment = decision.reviews().stream()
                        .map(DiditWebhookPayload.Review::comment)
                        .filter(c -> c != null && !c.isBlank())
                        .reduce((first, second) -> second)
                        .orElse(null);
                if (comment != null) {
                    return comment;
                }
            }
            if (decision.idVerifications() != null) {
                final String warnings = decision.idVerifications().stream()
                        .filter(v -> v.warnings() != null)
                        .flatMap(v -> v.warnings().stream())
                        .map(w -> w.shortDescription() != null ? w.shortDescription() : w.risk())
                        .filter(Objects::nonNull)
                        .distinct()
                        .reduce((a, b) -> a + ", " + b)
                        .orElse(null);
                if (warnings != null) {
                    return warnings;
                }
            }
        }
        return "Vérification d'identité refusée";
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
                p.getDiditSessionId(),
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
