# Diama — Prompts de développement backend Quarkus
# À utiliser pas à pas dans Claude Code ou dans une nouvelle conversation Claude

---

## Contexte global à coller en tête de chaque session

```
Je développe "Diama", une plateforme de transfert d'argent Europe ↔ Afrique de l'Ouest.

Stack backend :
- Java 21 + Quarkus 3.x (mode natif GraalVM)
- PostgreSQL 16 (base principale)
- Redis (cache taux de change, sessions)
- Kafka (messaging, outbox pattern)
- Keycloak 24 (authentification OIDC, realm: transfer-app)
- Thunes API v2 (payout Afrique)
- Swan.io (collecte EUR, IBAN dédiés)
- Sumsub (KYC / vérification identité)
- Docker + Kubernetes (déploiement)

Build : Gradle multi-modules (Kotlin DSL — build.gradle.kts)
Structure du projet :
- diama-api      (JAX-RS resources)
- diama-domain   (entités, services métier)
- diama-infra    (clients HTTP, repositories)
- diama-common   (DTOs, exceptions, utils)

Configuration : application.yaml (YAML, pas .properties)
Profils Quarkus : application-dev.yaml / application-prod.yaml

Base de données : schéma V1__init_schema.sql déjà défini (Flyway)
Tables principales : users (keycloak_id), transactions, kyc_profiles,
exchange_rates, fee_tiers, kafka_outbox, audit_events, admin_users, user_sessions

Authentification : Keycloak avec 3 clients
- mobile-app      (public, PKCE)
- backoffice-app  (confidential, session)
- quarkus-api     (confidential, client_credentials)
JWT contient : sub (keycloak_id), roles, kyc_level (custom claim)
```

---

## ÉTAPE 1 — Structure du projet Gradle multi-modules

```
En te basant sur le contexte Diama ci-dessus, génère la structure complète
du projet Gradle multi-modules Quarkus en Kotlin DSL.

Génère :

1. settings.gradle.kts (racine) :
   - rootProject.name = "diama"
   - include de tous les sous-modules

2. build.gradle.kts racine :
   - Plugin Quarkus platform BOM via io.quarkus.platform (version 3.x)
   - Configuration communes (Java 21, encoding UTF-8)
   - Bloc allprojects / subprojects avec dépendances partagées :
     * quarkus-resteasy-reactive-jackson
     * quarkus-hibernate-orm-panache
     * quarkus-jdbc-postgresql
     * quarkus-oidc
     * quarkus-smallrye-jwt
     * quarkus-redis-client
     * quarkus-smallrye-reactive-messaging-kafka
     * quarkus-flyway
     * quarkus-rest-client-reactive
     * quarkus-keycloak-admin-client
     * quarkus-smallrye-openapi
     * quarkus-smallrye-health
     * quarkus-micrometer
     * quarkus-container-image-docker
     * lombok + mapstruct
     * quarkus-junit5 + rest-assured (tests)

3. build.gradle.kts de chaque sous-module avec leurs dépendances propres :
   - diama-common   : aucune dépendance interne
   - diama-domain   : dépend de diama-common
   - diama-infra    : dépend de diama-common + diama-domain
   - diama-api      : dépend de tous les autres modules

4. Structure de packages Java pour chaque module :
   - diama-common : dto/, exception/, util/, validation/
   - diama-domain : user/, transaction/, kyc/, exchange/, fee/, audit/
   - diama-infra  : keycloak/, thunes/, swan/, sumsub/, kafka/
   - diama-api    : resource/, filter/, mapper/

5. src/main/resources/application.yaml principal (structure YAML complète) :
   quarkus:
     datasource:
       db-kind: postgresql
       jdbc.url: ${DB_URL:jdbc:postgresql://localhost:5432/diama}
       username: ${DB_USER:diama}
       password: ${DB_PASSWORD}
     flyway:
       migrate-at-start: true
       locations: classpath:db/migration
     oidc:
       auth-server-url: ${KEYCLOAK_URL}/realms/transfer-app
       client-id: quarkus-api
       credentials.secret: ${KEYCLOAK_API_SECRET}
     redis:
       hosts: ${REDIS_URL:redis://localhost:6379}
     kafka:
       bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
     http:
       port: 8080
       cors:
         origins: ${CORS_ORIGINS:http://localhost:3000}
   mp:
     messaging:
       outgoing:
         transfer-events.connector: smallrye-kafka
         kyc-events.connector: smallrye-kafka
         notifications.connector: smallrye-kafka
         thunes-callbacks.connector: smallrye-kafka
         audit-events.connector: smallrye-kafka
   diama:
     thunes:
       base-url: ${THUNES_URL:https://api.sandbox.thunes.com}
       api-key: ${THUNES_API_KEY}
       api-secret: ${THUNES_API_SECRET}
     swan:
       base-url: ${SWAN_URL:https://api.swan.io}
       client-id: ${SWAN_CLIENT_ID}
       client-secret: ${SWAN_CLIENT_SECRET}
     sumsub:
       base-url: ${SUMSUB_URL:https://api.sumsub.com}
       app-token: ${SUMSUB_APP_TOKEN}
       app-secret: ${SUMSUB_APP_SECRET}

6. src/main/resources/application-dev.yaml (profil dev) :
   - Quarkus DevServices activés (postgres, redis, kafka, keycloak)
   - Logs en DEBUG
   - swagger-ui always-include: true

7. src/main/resources/application-prod.yaml (profil prod) :
   - Logs JSON (quarkus-logging-json)
   - native image optimizations
   - swagger-ui désactivé

8. Dockerfile multi-stage pour build natif GraalVM :
   - Stage 1 : gradle build -Pquarkus.package.type=native
   - Stage 2 : image minimale ubi-minimal avec le binaire natif
```

---

## ÉTAPE 2 — Entités Panache et repositories

```
En te basant sur le contexte Diama et le schéma PostgreSQL V1__init_schema.sql,
génère toutes les entités Panache et leurs repositories.

Pour chaque entité, utilise le pattern PanacheEntityBase avec UUID comme PK.
Applique les annotations JPA complètes (@Table, @Column, @Enumerated, etc.)

Génère dans l'ordre :

1. Les enums Java correspondant aux types PostgreSQL :
   UserStatus, TxStatus, RfiStatus, KycVerificationStatus, DocType, DocStatus,
   AmlResult, OutboxStatus, NotificationChannel, NotificationStatus,
   AdminRole, SessionClient

2. Les entités (avec Lombok @Data, @Builder, @NoArgsConstructor) :
   - User (avec keycloak_id, sans password_hash)
   - UserBeneficiary
   - UserDevice
   - UserSession (avec keycloak_session_id, session_client)
   - AdminUser
   - Transaction (avec keycloak_session_id, initiator_client)
   - TransactionStatusHistory
   - RfiRequest
   - ThunesPayer
   - ExchangeRate (avec client_rate en colonne générée @Column(insertable=false, updatable=false))
   - FeeTier
   - KycProfile
   - KycDocument
   - AmlScreening
   - AuditEvent (avec keycloak_user_id, keycloak_session_id, keycloak_client)
   - KafkaOutbox
   - Notification

3. Les repositories (PanacheRepository) avec les méthodes custom :
   - UserRepository         : findByKeycloakId, findByEmail, findByPhone
   - TransactionRepository  : findByUserIdPaginated, findByThunesExternalId,
                              findByStatusAndCreatedAtBefore, countByUserIdThisMonth
   - KycProfileRepository   : findByUserId, findBySumsubApplicantId
   - ExchangeRateRepository : findActiveByCorridorAndDate
   - KafkaOutboxRepository  : findPendingForProcessing(int batchSize)

Utilise @Transactional sur les méthodes d'écriture.
```

---

## ÉTAPE 3 — Sécurité Keycloak et contexte utilisateur

```
En te basant sur le contexte Diama (Keycloak realm: transfer-app,
3 clients : mobile-app, backoffice-app, quarkus-api), génère la couche
de sécurité complète.

Génère :

1. SecurityContext (@ApplicationScoped) :
   - Injection de JsonWebToken (quarkus-smallrye-jwt)
   - getCurrentUserId()       → UUID depuis jwt.getSubject() (= keycloak_id)
   - getKycLevel()            → int depuis claim "kyc_level"
   - getCurrentAdminId()      → UUID depuis admin_users via keycloak_id
   - hasRole(String role)     → boolean
   - getKeycloakSessionId()   → String depuis claim "sid"
   - getClientId()            → String depuis claim "azp" (authorized party)
   - buildAuditContext()      → AuditContext record

2. UserSyncService (@ApplicationScoped) :
   - getOrCreateUser(JsonWebToken jwt) → User
   - Cherche par keycloak_id, crée si absent avec les claims JWT
   - Met à jour email/phone si changés dans Keycloak
   - @Transactional

3. KeycloakAdminService (@ApplicationScoped, client: quarkus-api) :
   - updateUserAttribute(String keycloakId, String attr, String value)
   - updateKycLevel(String keycloakId, int level)
   - assignRole(String keycloakId, String role)
   - revokeRole(String keycloakId, String role)
   - getUserSessions(String keycloakId) → List<SessionRepresentation>
   - invalidateSession(String sessionId)

4. RequestLoggingFilter (@ServerRequestFilter) :
   - Log chaque requête avec keycloak_user_id, session_id, IP
   - Insère dans user_sessions à la première requête de la session

5. Les annotations de sécurité sur les resources :
   @RolesAllowed("user") pour les endpoints mobile
   @RolesAllowed({"kyc-agent","admin"}) pour les endpoints KYC backoffice
   @RolesAllowed({"ops-agent","admin"}) pour les endpoints transactions backoffice
   @RolesAllowed("admin") pour la configuration

Inclus les tests unitaires avec @QuarkusTest et @TestSecurity.
```

---

## ÉTAPE 4 — Service de gestion des utilisateurs

```
En te basant sur le contexte Diama, génère le module complet de gestion
des utilisateurs.

Génère :

1. DTOs (records Java) :
   - UserProfileDto      (réponse publique, sans keycloak_id)
   - UserRegistrationDto (payload d'inscription)
   - UserUpdateDto       (mise à jour profil)
   - BeneficiaryDto + CreateBeneficiaryDto
   - DeviceRegistrationDto

2. UserService (@ApplicationScoped) :
   - getProfile(UUID userId)                                    → UserProfileDto
   - updateProfile(UUID userId, UserUpdateDto dto)              → UserProfileDto
   - listBeneficiaries(UUID userId)                             → List<BeneficiaryDto>
   - addBeneficiary(UUID userId, CreateBeneficiaryDto dto)      → BeneficiaryDto
   - deactivateBeneficiary(UUID userId, UUID beneficiaryId)
   - registerDevice(UUID userId, DeviceRegistrationDto dto)
   - checkMonthlyLimit(UUID userId, BigDecimal amount)          → LimitCheckResult record
     (available, used, limit, kyc_level)

3. UserResource (@Path("/api/v1/users"), @Authenticated) :
   - GET    /me                    → profil de l'utilisateur connecté
   - PUT    /me                    → mise à jour profil
   - GET    /me/beneficiaries
   - POST   /me/beneficiaries
   - DELETE /me/beneficiaries/{id}
   - POST   /me/devices            → enregistrement token push
   - GET    /me/limits             → plafonds KYC actuels

4. AuditService (@ApplicationScoped) :
   - log(AuditContext ctx, String eventType, String entityType,
         UUID entityId, Object before, Object after)
   - Sérialise before/after en JSONB via Jackson
   - Insère dans kafka_outbox (topic: "audit-events") de façon asynchrone

Inclus la validation Bean Validation (@NotNull, @Email, @Size, @Pattern)
sur tous les DTOs et les ExceptionMappers pour les erreurs métier.
```

---

## ÉTAPE 5 — Moteur de taux de change et frais

```
En te basant sur le contexte Diama, génère le moteur de calcul des
taux de change et des frais de transfert.

Génère :

1. ExchangeRateService (@ApplicationScoped) :
   - getActiveRate(String source, String dest, Long payerId)
     → ExchangeRate (depuis Redis en priorité, sinon PostgreSQL)
   - refreshRatesFromThunes() → appelé par scheduler quotidien
   - storeInRedis(ExchangeRate rate) → TTL 24h
   - getFromRedis(String corridor) → Optional<ExchangeRate>
   - Clé Redis : "rate:{source}:{dest}:{payerId}:{date}"

2. FeeCalculatorService (@ApplicationScoped) :
   - calculateFee(String currency, BigDecimal amount, Long payerId)
     → FeeResult record (fixedFee, percentFee, totalFee, tierApplied)
   - Cherche le tier actif correspondant au montant et au corridor
   - Logique : MAX(fixedFee, percentFee * amount) avec plafond si défini
   - Exemple de grille EUR :
     0–50 €    → 1.99 € fixe
     51–200 €  → 2.25 % (min 2.50 €)
     201–1000€ → 2.00 % (min 4.50 €)
     > 1000 €  → 1.50 % (min 20 €)

3. QuotationService (@ApplicationScoped) :
   - buildQuotation(UUID userId, BigDecimal sourceAmount,
                    String sourceCurrency, Long payerId)
     → QuotationDto record avec :
       sourceAmount, sourceCurrency, destAmount, destCurrency,
       exchangeRate, clientRate, feeAmount, totalDebit,
       payerName, estimatedDelivery, rateValidUntil (15 min)
   - Vérifie les limites KYC avant de retourner la quotation
   - Cache la quotation en Redis 15 minutes (clé: "quot:{userId}:{uuid}")

4. ExchangeRateResource (@Path("/api/v1/rates")) :
   - GET /current?from=EUR&to=GNF&payerId=1234 → taux du jour
   - GET /payers?country=GN                    → liste des opérateurs Thunes actifs

5. QuotationResource (@Path("/api/v1/quotations"), @RolesAllowed("user")) :
   - POST / → body: {sourceAmount, sourceCurrency, payerId}
            → retourne QuotationDto avec taux verrouillé 15 min

6. RateRefreshScheduler (@ApplicationScoped) :
   - @Scheduled(cron="0 0 6 * * ?") → refresh quotidien à 6h UTC
   - Appelle Thunes pour récupérer les taux par payer actif
   - Met à jour exchange_rates et Redis
   - Publie événement Kafka "rate-updated"

Utilise @CacheResult et @CacheInvalidate de Quarkus pour Redis.
```

---

## ÉTAPE 6 — Client Thunes et création de transaction

```
En te basant sur le contexte Diama et l'API Thunes v2, génère le client
Thunes complet et le service de création de transaction.

Génère :

1. ThunesClient (@ApplicationScoped, REST client Quarkus) :
   - Authentification : Basic Auth (api-key:api-secret encodé Base64)
   - Base URL depuis diama.thunes.base-url (application.yaml)
   - Timeout : connect 5s, read 30s
   - Méthodes :
     a. getPayers(String countryIso)                              → List<ThunesPayer>
     b. createQuotation(ThunesQuotationRequest)                   → ThunesQuotation
        POST /v2/money-transfer/quotations
     c. confirmTransaction(Long quotationId, ThunesTransactionRequest)
        → ThunesTransaction
        POST /v2/money-transfer/quotations/{id}/transactions
     d. getTransaction(Long id)                                   → ThunesTransaction
     e. uploadRFIDocument(Long rfiId, byte[] content, String mimeType)
     f. submitRFIInfo(Long rfiId, Map<String, String> info)
     g. syncPayers()                                              → List<ThunesPayer>

2. DTOs Thunes (records) :
   ThunesQuotationRequest, ThunesQuotation, ThunesTransactionRequest,
   ThunesSender, ThunesBeneficiary, ThunesCreditPartyIdentifier,
   ThunesTransaction, ThunesCallbackPayload, ThunesRFIPayload

3. TransferService (@ApplicationScoped) :
   a. initiateTransfer(UUID userId, CreateTransferRequest req) → TransferDto
      - Vérifie KYC level et limites mensuelles
      - Récupère la quotation depuis Redis (validée 15 min)
      - Crée Transaction en DB (status: INITIATED)
      - Publie event Kafka "transfer-initiated"

   b. confirmAndSendToThunes(UUID transactionId)
      - Appelle ThunesClient.confirmTransaction()
      - Transition FSM → CONFIRMED
      - Met à jour thunes_transaction_id en DB

   c. getTransfer(UUID transactionId, UUID userId)              → TransferDto
   d. listTransfers(UUID userId, int page, int size)            → Page<TransferDto>
   e. cancelTransfer(UUID transactionId, UUID userId)

4. FSM TransactionFSM (@ApplicationScoped) :
   - transition(UUID txId, TxStatus newStatus, String reason, UUID adminId)
   - Valide les transitions autorisées :
     INITIATED    → AML_CLEARED, FAILED, CANCELLED
     AML_CLEARED  → CONFIRMED, FAILED
     CONFIRMED    → UNDER_REVIEW, PROCESSING
     UNDER_REVIEW → PROCESSING, FAILED, CANCELLED
     PROCESSING   → DELIVERED, FAILED
     DELIVERED    → (terminal)
     FAILED       → REFUNDED
   - Insère dans transaction_status_history à chaque transition
   - Publie dans kafka_outbox

5. TransferResource (@Path("/api/v1/transfers"), @RolesAllowed("user")) :
   - POST /        → initier un transfert
   - GET  /        → historique paginé
   - GET  /{id}    → détail d'un transfert
   - DELETE /{id}  → annulation (si status INITIATED seulement)

DTOs : CreateTransferRequest, TransferDto, TransferStatusDto, Page<T>
Exceptions : InsufficientLimitException, InvalidTransferStateException,
             QuotationExpiredException
```

--- 

## ÉTAPE 7 — Webhook Thunes et notifications
```
En te basant sur le contexte Diama, génère le handler de webhooks Thunes
et le système de notifications.

Génère :

1. ThunesWebhookResource (@Path("/webhooks/thunes"), pas d'auth Keycloak) :
   - POST / → reçoit le callback Thunes (ThunesCallbackPayload)
   - Répond HTTP 200 en moins de 5 secondes (traitement asynchrone)
   - Vérification HMAC-SHA256 de la signature (header X-Thunes-Signature)
   - Mapping statuts Thunes → TxStatus interne :
     10000 → CONFIRMED
     20000 → UNDER_REVIEW (déclenche RFI handling)
     40000 → PROCESSING
     70000 → DELIVERED
     80000 → FAILED
     90000 → FAILED (DECLINED)
   - Publie dans kafka_outbox topic "thunes-callbacks"

2. ThunesCallbackConsumer (@ApplicationScoped, @Incoming("thunes-callbacks")) :
   - Appelle TransactionFSM.transition() avec le nouveau statut
   - Si UNDER_REVIEW : appelle RFIService.handleRFI()
   - Si DELIVERED    : déclenche notifications + met à jour monthly_used
   - Si FAILED       : déclenche remboursement Swan

3. RFIService (@ApplicationScoped) :
   - handleRFI(String externalId, ThunesRFIPayload rfi)
     → persiste RfiRequest, notifie backoffice + utilisateur
   - submitRFIResponse(UUID rfiId, List<RFIDocument> docs,
                       Map<String, String> info)
   - getRFIPendingForAdmin() → List<RfiRequest>

4. NotificationService (@ApplicationScoped) :
   - send(UUID userId, UUID txId, String templateKey, Map<String,Object> ctx)
   - Sélectionne les canaux (PUSH, SMS, EMAIL)
   - Insère dans table notifications + publie kafka_outbox "notifications"

5. NotificationConsumer (@ApplicationScoped, @Incoming("notifications")) :
   - PUSH  : FCM/APNS via device_token
   - SMS   : Twilio ou OVH SMS
   - EMAIL : SendGrid ou SMTP
   - Met à jour statut (SENT / FAILED + retry_count)

6. SwanRefundService (@ApplicationScoped) :
   - refundUser(Transaction tx) → virement retour Swan → IBAN expéditeur
   - Transition FSM → REFUNDED + log audit_events

Templates : TRANSFER_INITIATED, TRANSFER_AML_CLEARED, TRANSFER_PROCESSING,
            TRANSFER_DELIVERED, TRANSFER_FAILED, TRANSFER_REFUNDED,
            RFI_DOCUMENT_REQUIRED, KYC_APPROVED, KYC_REJECTED
```

---

## ÉTAPE 8 — Module KYC (Sumsub)
```
En te basant sur le contexte Diama (Sumsub pour KYC, niveaux 1 à 4), génère
le module KYC complet.

Génère :

1. SumsubClient (@ApplicationScoped, REST client) :
   - Authentification : HMAC-SHA256 sur chaque requête
     (headers X-App-Token, X-App-Access-Sig, X-App-Access-Ts)
   - createApplicant(String externalUserId, String levelName) → SumsubApplicant
   - generateAccessToken(String applicantId)                  → String (token SDK)
   - getApplicantStatus(String applicantId)                   → SumsubReviewResult
   - getApplicantDocuments(String applicantId)                → List<SumsubDocument>

2. KycService (@ApplicationScoped) :
   a. initKyc(UUID userId) → KycInitDto (token Sumsub pour SDK mobile, 30 min)
   b. handleSumsubWebhook(SumsubWebhookPayload payload)
      - applicantReviewed APPROVED → upgradeKycLevel()
      - applicantReviewed REJECTED → setRejected() + notification
   c. upgradeKycLevel(UUID userId, int newLevel)
      - Met à jour kyc_profiles (level, monthly_limit, verification_status)
      - Met à jour l'attribut Keycloak via KeycloakAdminService
      - Le trigger sync_kyc_level_to_keycloak publie dans kafka_outbox
   d. manualReview(UUID kycProfileId, boolean approved, String reason, UUID adminId)
   e. getKycStatus(UUID userId) → KycStatusDto

3. KycResource (@Path("/api/v1/kyc"), @RolesAllowed("user")) :
   - POST /init   → initialise KYC, retourne token Sumsub pour SDK mobile
   - GET  /status → statut et niveau KYC de l'utilisateur connecté

4. SumsubWebhookResource (@Path("/webhooks/sumsub")) :
   - POST / → vérifie X-App-Access-Sig, dispatch vers KycService async

5. AdminKycResource (@Path("/api/v1/admin/kyc"), @RolesAllowed("kyc-agent")) :
   - GET  /pending         → dossiers en attente de revue manuelle
   - GET  /{userId}        → dossier KYC complet
   - POST /{userId}/approve → validation manuelle
   - POST /{userId}/reject  → rejet avec motif

DTOs : KycInitDto, KycStatusDto, KycDocumentDto,
       SumsubWebhookPayload, SumsubReviewResult, AdminKycReviewDto
```

---

## ÉTAPE 9 — Module Swan (collecte EUR)
```
En te basant sur le contexte Diama (Swan.io pour la collecte EUR,
IBAN dédié par utilisateur), génère le module Swan complet.

Génère :

1. SwanClient (@ApplicationScoped, GraphQL + REST) :
   - Authentification OAuth2 client_credentials
   - createAccount(SwanAccountRequest)          → SwanAccount (avec IBAN FR76)
   - getAccountBalance(String accountId)        → BigDecimal
   - initiateTransfer(SwanTransferRequest)      → SwanTransfer
   - getTransaction(String transactionId)       → SwanTransaction

2. SwanAccountService (@ApplicationScoped) :
   - provisionAccount(UUID userId)              → SwanAccount
     Crée le compte Swan à la validation KYC niveau 2
     Stocke swan_account_id et swan_iban dans users
   - getAccountForUser(UUID userId)             → SwanAccount
   - validateFundsReceived(String swanAccountId, BigDecimal expected) → boolean

3. SwanWebhookResource (@Path("/webhooks/swan")) :
   - POST / → vérifie signature Swan
   - Si TRANSACTION_BOOKED :
     → trouve la transaction INITIATED par montant + IBAN
     → appelle TransferService.confirmAndSendToThunes()
   - Si TRANSACTION_REJECTED : transition FSM → FAILED

4. FundsVerificationService (@ApplicationScoped) :
   - reconcile()              → scheduler horaire, cohérence Swan ↔ PostgreSQL
   - detectOrphanedFunds()    → montants Swan sans transaction associée
   - alertBackoffice(List<OrphanedFund>)

DTOs : SwanAccountRequest, SwanAccount, SwanTransferRequest,
       SwanTransfer, SwanWebhookEvent, OrphanedFund
```

---

## ÉTAPE 10 — Kafka outbox processor et audit
# NEXT
```
En te basant sur le contexte Diama (outbox pattern sur kafka_outbox),
génère le processeur d'outbox et le service d'audit complet.

Génère :

1. OutboxProcessor (@ApplicationScoped) :
   - @Scheduled(every="5s") → poll kafka_outbox WHERE status='PENDING'
   - Publie sur le bon topic selon kafka_topic
   - Met à jour status → PUBLISHED ou FAILED (+ retry_count)
   - Max 3 retries, puis FAILED + alerte
   - Batch de 50 messages max par cycle
   - @Transactional : atomicité update + publish

2. Topics Kafka et consumers :
   a. "transfer-events"    → TransferEventConsumer
      Met à jour monthly_used dans kyc_profiles à chaque DELIVERED
   b. "kyc-events"         → KycEventConsumer
      Appelle KeycloakAdminService.updateKycLevel() sur KYC_LEVEL_CHANGED
      Provisionne compte Swan si niveau 2 atteint
   c. "notifications"      → NotificationConsumer (défini étape 7)
   d. "thunes-callbacks"   → ThunesCallbackConsumer (défini étape 7)
   e. "rate-updated"       → RateCacheConsumer (invalide cache Redis)

3. AuditService (@ApplicationScoped) :
   - log(AuditEvent event) → insert dans kafka_outbox topic "audit-events"
   - buildFromContext(SecurityContext ctx, ...) → AuditEvent
   - AuditEvent record : userId, adminId, keycloakUserId, keycloakSessionId,
     keycloakClient, eventType, entityType, entityId, before, after, ip

4. AdminAuditResource (@Path("/api/v1/admin/audit"), @RolesAllowed("admin")) :
   - GET    /          → liste paginée avec filtres (userId, eventType, dates)
   - GET    /export    → export CSV 30 jours (streaming)
   - GET    /sessions  → v_active_sessions temps réel
   - DELETE /sessions/{keycloakSessionId} → invalide session Keycloak

5. MetricsService (@ApplicationScoped, Micrometer) :
   Expose via /q/metrics :
   - transfer_count       (par statut, par corridor)
   - transfer_volume_eur  (total, par corridor)
   - kyc_approval_rate
   - rfi_pending_count
   - kafka_outbox_pending_count
   - active_sessions_count
```

---

## ÉTAPE 11 — Backoffice API (admin endpoints)

```
En te basant sur le contexte Diama, génère tous les endpoints du backoffice
d'administration (Next.js consommera ces APIs).

Génère les resources suivantes (toutes sous /api/v1/admin/) :

1. AdminUserResource (@RolesAllowed({"kyc-agent","ops-agent","admin"})) :
   - GET  /users                                   → liste paginée avec filtres
   - GET  /users/{id}                              → profil complet (user + kyc + tx récentes)
   - PUT  /users/{id}/status                       → ACTIVE | SUSPENDED | CLOSED
   - GET  /users/{id}/transactions                 → historique complet
   - GET  /users/{id}/sessions                     → sessions Keycloak actives
   - POST /users/{id}/sessions/{sessionId}/revoke  → révocation session

2. AdminTransactionResource (@RolesAllowed({"ops-agent","admin"})) :
   - GET  /transactions                            → liste paginée avec filtres
   - GET  /transactions/{id}                       → détail + historique FSM
   - POST /transactions/{id}/force-status          → transition manuelle (motif obligatoire)
   - GET  /transactions/rfi-pending                → toutes les RFI en attente
   - POST /transactions/{id}/rfi/{rfiId}/submit    → soumettre docs RFI Thunes

3. AdminRateResource (@RolesAllowed("admin")) :
   - GET  /rates                    → taux actifs par corridor
   - PUT  /rates/{id}/spread        → modifier le spread (%)
   - POST /rates/refresh            → refresh manuel depuis Thunes
   - GET  /rates/history            → ?corridor=EUR-GNF

4. AdminFeeResource (@RolesAllowed("admin")) :
   - GET    /fees      → grille tarifaire active
   - POST   /fees      → créer un nouveau palier
   - PUT    /fees/{id} → modifier (valid_until sur l'ancien)
   - DELETE /fees/{id} → désactiver

5. AdminDashboardResource (@RolesAllowed({"ops-agent","admin"})) :
   - GET /dashboard/summary   → {txCount, txVolume, txSuccessRate,
                                  pendingRFI, pendingKYC, activeSessions, revenueToday}
   - GET /dashboard/corridors → stats par corridor (v_corridor_stats)
   - GET /dashboard/hourly    → volume horaire des 24 dernières heures

Pour tous les endpoints admin :
- Pagination : ?page=0&size=20&sort=createdAt,desc
- Log systématique dans audit_events (via AuditService)
- DTOs distincts des DTOs publics (AdminTransactionDto plus riche)
```

---

## ÉTAPE 12 — Tests, OpenAPI et finalisation

```
En te basant sur le contexte Diama, génère la couche de tests et
la configuration OpenAPI/Swagger.

Génère :

1. Tests d'intégration (@QuarkusIntegrationTest) :
   - TransferFlowIT : flux complet
     inscription → KYC niveau 1 → quotation → initiation
     → webhook Thunes DELIVERED → vérification monthly_used mis à jour
   - KycFlowIT : init Sumsub → webhook approved → upgrade niveau 2
     → vérification attribut Keycloak mis à jour
   - RFIFlowIT : transaction → webhook UNDER_REVIEW → soumission docs → PROCESSING

2. Tests unitaires (@QuarkusTest avec @InjectMock) :
   - FeeCalculatorServiceTest : tous les paliers de frais
   - ExchangeRateServiceTest  : cache Redis hit/miss
   - TransactionFSMTest       : toutes les transitions valides et invalides
   - QuotationServiceTest     : expiration quotation à 15 min

3. Mocks WireMock pour APIs externes :
   - ThunesClientMock  : quotation, confirmation, webhooks
   - SumsubClientMock  : création applicant, approbation
   - SwanClientMock    : création compte, webhook TRANSACTION_BOOKED

4. Configuration OpenAPI (SmallRye OpenAPI) dans application.yaml :
   quarkus:
     smallrye-openapi:
       info-title: Diama API
       info-version: 1.0.0
       security-scheme: jwt
       security-scheme-name: bearerAuth
   - @OpenAPIDefinition, @Operation, @APIResponse, @Parameter sur tous les endpoints
   - Tags : users, transfers, kyc, rates, admin
   - /q/swagger-ui activé uniquement en profil dev (application-dev.yaml)

5. Healthchecks (@ApplicationScoped, SmallRye Health) :
   - DatabaseHealthCheck  : ping PostgreSQL
   - RedisHealthCheck     : ping Redis
   - KafkaHealthCheck     : consumer group lag
   - ThunesHealthCheck    : GET /v2/money-transfer/payers
   - KeycloakHealthCheck  : GET /.well-known/openid-configuration

6. docker-compose.yml complet (développement local) :
   services:
     postgres     (port 5432, init V1__init_schema.sql)
     redis        (port 6379)
     kafka        (port 9092, avec kafka-ui sur 8090)
     keycloak     (port 8180, import realm-transfer-app.json)
     diama-api    (port 8080, hot reload Quarkus dev mode)
   + .env.example avec toutes les variables requises

7. gradle/wrapper/gradle-wrapper.properties avec la version Gradle stable.

Inclus un README.md avec :
- Prérequis (Java 21, Docker)
- Commandes : ./gradlew quarkusDev, ./gradlew test, ./gradlew build -Pnative
- URLs locales : API http://localhost:8080, Swagger http://localhost:8080/q/swagger-ui,
  Keycloak http://localhost:8180, Kafka UI http://localhost:8090
```

---

## Notes d'utilisation

- **Ordre recommandé** : suivre les étapes 1 → 12 dans l'ordre, chaque étape
  s'appuie sur les précédentes.
- **Contexte** : toujours coller le bloc "Contexte global" en tête de session
  avant de coller le prompt de l'étape.
- **Itération** : après chaque étape, demandez "ajoute les tests unitaires pour
  ce module" ou "génère la migration Flyway V2 pour ces changements".
- **Claude Code** : ces prompts sont optimisés pour Claude Code qui a accès
  à votre filesystem — il pourra créer les fichiers directement dans le projet.
- **YAML** : toute configuration doit aller dans application.yaml (jamais
  application.properties). Les secrets sont toujours via ${ENV_VAR} sans valeur
  par défaut en prod, avec une valeur de dev en default pour le profil local.
