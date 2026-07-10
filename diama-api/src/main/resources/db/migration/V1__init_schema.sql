-- =============================================================
--  Money Transfer Platform — PostgreSQL 16 Schema
--  Flyway migration: V1__init_schema.sql
--  Domains: users, transactions, configuration, KYC, audit
--  Auth: Keycloak (keycloak_id = clé pivot, pas de password_hash)
-- =============================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -------------------------------------------------------------
-- uuid_generate_v7() : PostgreSQL 16 ne fournit pas nativement
-- l'UUID v7 (time-ordered). On l'implémente : 48 bits de
-- timestamp Unix (ms) en tête, version 7, variant RFC 4122.
-- Utilisé comme DEFAULT des PK et par les triggers (kafka_outbox).
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION uuid_generate_v7()
RETURNS uuid
AS $$
BEGIN
  RETURN encode(
    set_bit(
      set_bit(
        overlay(
          uuid_send(gen_random_uuid())
          PLACING substring(
            int8send(floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint)
            FROM 3
          )
          FROM 1 FOR 6
        ),
        52, 1
      ),
      53, 1
    ),
    'hex'
  )::uuid;
END
$$ LANGUAGE plpgsql VOLATILE;

-- =============================================================
--  DOMAIN 1 — USERS
-- =============================================================

CREATE TYPE user_status AS ENUM (
  'PENDING_VERIFICATION',
  'ACTIVE',
  'SUSPENDED',
  'CLOSED'
);

CREATE TABLE users (
  id                    UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  -- Keycloak : sub du JWT — clé de jointure entre Keycloak et notre DB
  keycloak_id           VARCHAR(100) NOT NULL UNIQUE,
  email                 VARCHAR(255) NOT NULL UNIQUE,
  phone                 VARCHAR(30)  NOT NULL UNIQUE,
  -- Pas de password_hash : Keycloak gère l'authentification
  first_name            VARCHAR(100) NOT NULL,
  last_name             VARCHAR(100) NOT NULL,
  date_of_birth         DATE,
  nationality           CHAR(3),                   -- ISO 3166-1 alpha-3
  country_of_residence  CHAR(2) NOT NULL,          -- ISO 3166-1 alpha-2
  preferred_currency    CHAR(3) NOT NULL DEFAULT 'EUR',
  status                user_status NOT NULL DEFAULT 'PENDING_VERIFICATION',
  swan_account_id       VARCHAR(100),              -- Swan wallet ID
  swan_iban             VARCHAR(34),               -- IBAN dédié Swan
  referral_code         VARCHAR(20) UNIQUE,
  referred_by           UUID REFERENCES users(id),
  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_keycloak  ON users(keycloak_id);
CREATE INDEX idx_users_email     ON users(email);
CREATE INDEX idx_users_phone     ON users(phone);
CREATE INDEX idx_users_status    ON users(status);

-- ---------------------------------------------------------------

CREATE TABLE user_beneficiaries (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  nickname          VARCHAR(100),
  first_name        VARCHAR(100) NOT NULL,
  last_name         VARCHAR(100) NOT NULL,
  phone_msisdn      VARCHAR(30),                  -- +224XXXXXXXXX
  bank_account_no   VARCHAR(50),
  swift_bic         VARCHAR(11),
  country_iso       CHAR(2) NOT NULL,
  currency          CHAR(3) NOT NULL,
  thunes_payer_id   BIGINT,                       -- Payer Thunes
  payer_service     VARCHAR(50),                  -- 'MobileWallet','BankAccount','CashPickup'
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_beneficiaries_user ON user_beneficiaries(user_id);

-- ---------------------------------------------------------------

CREATE TABLE user_devices (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_token    VARCHAR(500) NOT NULL,          -- FCM / APNS token
  platform        VARCHAR(10) NOT NULL,           -- 'IOS' | 'ANDROID'
  app_version     VARCHAR(20),
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  registered_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_devices_user ON user_devices(user_id);

-- ---------------------------------------------------------------
-- Sessions Keycloak — suivi des connexions actives

CREATE TYPE session_client AS ENUM ('mobile-app', 'backoffice-app');

CREATE TABLE user_sessions (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  keycloak_session_id VARCHAR(100) NOT NULL UNIQUE, -- sid du JWT Keycloak
  client              session_client NOT NULL,
  ip_address          INET,
  user_agent          TEXT,
  device_id           UUID REFERENCES user_devices(id),
  is_active           BOOLEAN NOT NULL DEFAULT TRUE,
  started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ended_at            TIMESTAMPTZ
);

CREATE INDEX idx_sessions_user       ON user_sessions(user_id);
CREATE INDEX idx_sessions_kc_session ON user_sessions(keycloak_session_id);
CREATE INDEX idx_sessions_active     ON user_sessions(is_active, last_seen_at DESC);

-- ---------------------------------------------------------------
-- Agents backoffice (kyc-agent, ops-agent, admin)

CREATE TYPE admin_role AS ENUM ('kyc-agent', 'ops-agent', 'admin', 'super-admin');

CREATE TABLE admin_users (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  keycloak_id     VARCHAR(100) NOT NULL UNIQUE,   -- sub Keycloak de l'agent
  email           VARCHAR(255) NOT NULL UNIQUE,
  full_name       VARCHAR(200) NOT NULL,
  role            admin_role NOT NULL,
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_at   TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_keycloak ON admin_users(keycloak_id);
CREATE INDEX idx_admin_role     ON admin_users(role);

-- =============================================================
--  DOMAIN 2 — TRANSACTIONS
-- =============================================================

CREATE TYPE tx_status AS ENUM (
  'INITIATED',
  'AML_CLEARED',
  'CONFIRMED',
  'UNDER_REVIEW',
  'PROCESSING',
  'DELIVERED',
  'FAILED',
  'REFUNDED',
  'CANCELLED'
);

CREATE TABLE transactions (
  id                    UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  user_id               UUID NOT NULL REFERENCES users(id),
  beneficiary_id        UUID NOT NULL REFERENCES user_beneficiaries(id),
  status                tx_status NOT NULL DEFAULT 'INITIATED',

  -- Montants (NUMERIC pour précision financière exacte, jamais FLOAT)
  source_currency       CHAR(3) NOT NULL,          -- 'EUR'
  source_amount         NUMERIC(18,2) NOT NULL,
  dest_currency         CHAR(3) NOT NULL,          -- 'GNF' | 'XOF'
  dest_amount           NUMERIC(18,2),
  exchange_rate         NUMERIC(18,6),             -- taux appliqué
  fee_amount            NUMERIC(18,2),
  fee_currency          CHAR(3) NOT NULL DEFAULT 'EUR',

  -- Références Thunes
  thunes_quotation_id   BIGINT,
  thunes_transaction_id BIGINT,
  thunes_external_id    VARCHAR(100) UNIQUE,       -- notre UUID envoyé à Thunes
  thunes_payer_id       BIGINT,

  -- Références Swan (collecte EUR)
  swan_transfer_id      VARCHAR(100),
  swan_account_id       VARCHAR(100),

  -- Contexte Keycloak de la session ayant initié le transfert
  keycloak_session_id   VARCHAR(100),              -- sid du JWT pour audit
  initiator_client      session_client,            -- 'mobile-app' uniquement ici

  -- Métadonnées
  sender_ip             INET,
  device_id             UUID REFERENCES user_devices(id),
  failure_reason        TEXT,
  refund_amount         NUMERIC(18,2),
  refund_at             TIMESTAMPTZ,

  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at          TIMESTAMPTZ
);

CREATE INDEX idx_tx_user_id       ON transactions(user_id);
CREATE INDEX idx_tx_status        ON transactions(status);
CREATE INDEX idx_tx_thunes_ext    ON transactions(thunes_external_id);
CREATE INDEX idx_tx_created_at    ON transactions(created_at DESC);
CREATE INDEX idx_tx_user_status   ON transactions(user_id, status);
CREATE INDEX idx_tx_kc_session    ON transactions(keycloak_session_id);

-- ---------------------------------------------------------------

CREATE TABLE transaction_status_history (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  transaction_id  UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
  from_status     tx_status,
  to_status       tx_status NOT NULL,
  triggered_by    VARCHAR(50),                    -- 'WEBHOOK_THUNES' | 'USER' | 'ADMIN:{uuid}'
  admin_id        UUID REFERENCES admin_users(id), -- si action manuelle backoffice
  metadata        JSONB,                          -- payload brut Thunes, etc.
  changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tx_history_tx ON transaction_status_history(transaction_id);

-- ---------------------------------------------------------------

CREATE TYPE rfi_status AS ENUM ('PENDING', 'SUBMITTED', 'RESOLVED', 'EXPIRED');

CREATE TABLE rfi_requests (
  id                    UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  transaction_id        UUID NOT NULL REFERENCES transactions(id),
  thunes_rfi_id         BIGINT NOT NULL,
  status                rfi_status NOT NULL DEFAULT 'PENDING',
  requested_info        JSONB,                    -- {"sender":["date_of_birth"]}
  requested_docs        JSONB,                    -- ["identification_documents"]
  thunes_description    TEXT,
  expires_at            TIMESTAMPTZ,
  submitted_at          TIMESTAMPTZ,
  submitted_by          UUID REFERENCES admin_users(id), -- agent ops qui a soumis
  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rfi_transaction ON rfi_requests(transaction_id);
CREATE INDEX idx_rfi_status      ON rfi_requests(status);

-- =============================================================
--  DOMAIN 3 — CONFIGURATION
-- =============================================================

CREATE TABLE thunes_payers (
  id              BIGINT PRIMARY KEY,             -- ID Thunes natif
  name            VARCHAR(200) NOT NULL,
  country_iso     CHAR(2) NOT NULL,
  currency        CHAR(3) NOT NULL,
  service_type    VARCHAR(50) NOT NULL,           -- 'MobileWallet' | 'BankAccount' | 'CashPickup'
  min_amount      NUMERIC(18,2),
  max_amount      NUMERIC(18,2),
  precision       SMALLINT DEFAULT 2,
  increment       NUMERIC(10,4),
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  synced_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payers_country  ON thunes_payers(country_iso);
CREATE INDEX idx_payers_currency ON thunes_payers(currency);

-- ---------------------------------------------------------------

CREATE TABLE exchange_rates (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  source_currency   CHAR(3) NOT NULL,             -- 'EUR'
  dest_currency     CHAR(3) NOT NULL,             -- 'GNF' | 'XOF'
  thunes_payer_id   BIGINT REFERENCES thunes_payers(id),
  rate              NUMERIC(18,6) NOT NULL,        -- taux brut
  spread_pct        NUMERIC(5,4) NOT NULL DEFAULT 0.02, -- 2% marge
  client_rate       NUMERIC(18,6) GENERATED ALWAYS AS (rate * (1 - spread_pct)) STORED,
  effective_date    DATE NOT NULL,
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by        UUID REFERENCES admin_users(id), -- admin qui a mis à jour
  fetched_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (source_currency, dest_currency, thunes_payer_id, effective_date)
);

CREATE INDEX idx_rates_corridor ON exchange_rates(source_currency, dest_currency, is_active);
CREATE INDEX idx_rates_date     ON exchange_rates(effective_date DESC);

-- ---------------------------------------------------------------

CREATE TABLE fee_tiers (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  source_currency   CHAR(3) NOT NULL,
  dest_currency     CHAR(3),
  min_amount        NUMERIC(18,2) NOT NULL,
  max_amount        NUMERIC(18,2),
  fixed_fee         NUMERIC(18,2) NOT NULL DEFAULT 0,
  percent_fee       NUMERIC(5,4) NOT NULL DEFAULT 0,
  min_fee           NUMERIC(18,2),
  max_fee           NUMERIC(18,2),
  is_active         BOOLEAN NOT NULL DEFAULT TRUE,
  valid_from        DATE NOT NULL DEFAULT CURRENT_DATE,
  valid_until       DATE,
  created_by        UUID REFERENCES admin_users(id), -- admin ayant créé la règle
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fees_corridor ON fee_tiers(source_currency, dest_currency, is_active);

-- ---------------------------------------------------------------

CREATE TABLE system_config (
  key         VARCHAR(100) PRIMARY KEY,
  value       TEXT NOT NULL,
  description TEXT,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_by  UUID REFERENCES admin_users(id)
);

INSERT INTO system_config(key, value, description) VALUES
  ('kyc_level1_monthly_limit', '300',   'Plafond mensuel KYC niveau 1 (EUR)'),
  ('kyc_level2_monthly_limit', '3000',  'Plafond mensuel KYC niveau 2 (EUR)'),
  ('kyc_level3_monthly_limit', '15000', 'Plafond mensuel KYC niveau 3 (EUR)'),
  ('aml_high_risk_threshold',  '1000',  'Seuil déclenchant screening AML renforcé (EUR)'),
  ('rate_cache_ttl_hours',     '24',    'Durée de vie du taux de change en cache Redis'),
  ('keycloak_realm',           'transfer-app', 'Nom du realm Keycloak'),
  ('keycloak_token_ttl_sec',   '900',   'Durée de vie access token (secondes)');

-- =============================================================
--  DOMAIN 4 — KYC / COMPLIANCE
-- =============================================================

CREATE TYPE kyc_verification_status AS ENUM (
  'NOT_STARTED',
  'PENDING',
  'APPROVED',
  'REJECTED',
  'ON_HOLD'
);

CREATE TABLE kyc_profiles (
  id                    UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  user_id               UUID NOT NULL UNIQUE REFERENCES users(id),
  kyc_level             SMALLINT NOT NULL DEFAULT 1 CHECK (kyc_level BETWEEN 1 AND 4),
  sumsub_applicant_id   VARCHAR(100) UNIQUE,
  verification_status   kyc_verification_status NOT NULL DEFAULT 'NOT_STARTED',
  risk_score            SMALLINT,
  risk_category         VARCHAR(20),              -- 'LOW' | 'MEDIUM' | 'HIGH'
  monthly_limit         NUMERIC(18,2) NOT NULL DEFAULT 300,
  monthly_used          NUMERIC(18,2) NOT NULL DEFAULT 0,
  limit_reset_date      DATE NOT NULL DEFAULT (DATE_TRUNC('month', NOW()) + INTERVAL '1 month'),
  level_granted_at      TIMESTAMPTZ,
  rejection_reason      TEXT,
  -- Agent ayant validé/rejeté manuellement
  reviewed_by           UUID REFERENCES admin_users(id),
  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kyc_user       ON kyc_profiles(user_id);
CREATE INDEX idx_kyc_sumsub     ON kyc_profiles(sumsub_applicant_id);
CREATE INDEX idx_kyc_status     ON kyc_profiles(verification_status);

-- ---------------------------------------------------------------

CREATE TYPE doc_type AS ENUM (
  'NATIONAL_ID',
  'PASSPORT',
  'RESIDENCE_PERMIT',
  'PROOF_OF_ADDRESS',
  'SOURCE_OF_FUNDS',
  'SELFIE'
);

CREATE TYPE doc_status AS ENUM ('PENDING', 'VERIFIED', 'REJECTED');

CREATE TABLE kyc_documents (
  id                UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  kyc_profile_id    UUID NOT NULL REFERENCES kyc_profiles(id),
  doc_type          doc_type NOT NULL,
  sumsub_doc_id     VARCHAR(100),
  status            doc_status NOT NULL DEFAULT 'PENDING',
  rejection_reason  TEXT,
  verified_by       UUID REFERENCES admin_users(id), -- agent KYC
  verified_at       TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_docs_kyc_profile ON kyc_documents(kyc_profile_id);

-- ---------------------------------------------------------------

CREATE TYPE aml_result AS ENUM ('CLEAR', 'HIT', 'FALSE_POSITIVE', 'PENDING_REVIEW');

CREATE TABLE aml_screenings (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  user_id         UUID NOT NULL REFERENCES users(id),
  transaction_id  UUID REFERENCES transactions(id),
  provider        VARCHAR(50) NOT NULL DEFAULT 'THUNES_FORTRESS',
  result          aml_result NOT NULL,
  risk_score      NUMERIC(5,2),
  matched_lists   JSONB,
  raw_response    JSONB,
  reviewed_by     UUID REFERENCES admin_users(id), -- si revue manuelle
  screened_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_aml_user        ON aml_screenings(user_id);
CREATE INDEX idx_aml_transaction ON aml_screenings(transaction_id);
CREATE INDEX idx_aml_result      ON aml_screenings(result);

-- =============================================================
--  DOMAIN 5 — AUDIT / ÉVÉNEMENTS
-- =============================================================

CREATE TABLE audit_events (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  -- Acteur de l'action (utilisateur final ou agent backoffice)
  user_id             UUID REFERENCES users(id),
  admin_id            UUID REFERENCES admin_users(id),
  transaction_id      UUID REFERENCES transactions(id),
  -- Contexte Keycloak pour traçabilité complète
  keycloak_user_id    VARCHAR(100),              -- sub du JWT
  keycloak_session_id VARCHAR(100),              -- sid du JWT
  keycloak_client     VARCHAR(100),              -- 'mobile-app' | 'backoffice-app'
  -- Événement
  event_type          VARCHAR(100) NOT NULL,
  entity_type         VARCHAR(50),
  entity_id           UUID,
  before_state        JSONB,
  after_state         JSONB,
  ip_address          INET,
  user_agent          TEXT,
  occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user       ON audit_events(user_id);
CREATE INDEX idx_audit_admin      ON audit_events(admin_id);
CREATE INDEX idx_audit_tx         ON audit_events(transaction_id);
CREATE INDEX idx_audit_type       ON audit_events(event_type);
CREATE INDEX idx_audit_time       ON audit_events(occurred_at DESC);
CREATE INDEX idx_audit_kc_user    ON audit_events(keycloak_user_id);
CREATE INDEX idx_audit_kc_session ON audit_events(keycloak_session_id);

-- ---------------------------------------------------------------
-- Outbox pattern : garantie de publication Kafka même en cas de crash

CREATE TYPE outbox_status AS ENUM ('PENDING', 'PUBLISHED', 'FAILED');

CREATE TABLE kafka_outbox (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  aggregate_id    UUID NOT NULL,
  aggregate_type  VARCHAR(50) NOT NULL,           -- 'TRANSACTION' | 'USER' | 'KYC'
  event_type      VARCHAR(100) NOT NULL,
  payload         JSONB NOT NULL,
  kafka_topic     VARCHAR(200),
  status          outbox_status NOT NULL DEFAULT 'PENDING',
  retry_count     SMALLINT NOT NULL DEFAULT 0,
  error_message   TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  processed_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_status     ON kafka_outbox(status, created_at);
CREATE INDEX idx_outbox_aggregate  ON kafka_outbox(aggregate_id);

-- ---------------------------------------------------------------

CREATE TYPE notification_channel AS ENUM ('PUSH', 'SMS', 'EMAIL');
CREATE TYPE notification_status  AS ENUM ('PENDING', 'SENT', 'FAILED');

CREATE TABLE notifications (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  user_id         UUID NOT NULL REFERENCES users(id),
  transaction_id  UUID REFERENCES transactions(id),
  channel         notification_channel NOT NULL,
  template_key    VARCHAR(100) NOT NULL,
  context         JSONB,
  status          notification_status NOT NULL DEFAULT 'PENDING',
  provider_ref    VARCHAR(200),
  sent_at         TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_user ON notifications(user_id);
CREATE INDEX idx_notif_tx   ON notifications(transaction_id);

-- =============================================================
--  TRIGGERS — updated_at automatique
-- =============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_transactions_updated_at
  BEFORE UPDATE ON transactions
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_kyc_profiles_updated_at
  BEFORE UPDATE ON kyc_profiles
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_admin_users_updated_at
  BEFORE UPDATE ON admin_users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Mise à jour last_seen_at des sessions automatiquement
CREATE OR REPLACE FUNCTION touch_session()
RETURNS TRIGGER AS $$
BEGIN NEW.last_seen_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sessions_touch
  BEFORE UPDATE ON user_sessions
  FOR EACH ROW EXECUTE FUNCTION touch_session();

-- Sync automatique kyc_level → attribut Keycloak via outbox
-- (le consumer Kafka appellera l'Admin API Keycloak pour mettre à jour l'attribut)
CREATE OR REPLACE FUNCTION sync_kyc_level_to_keycloak()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.kyc_level <> OLD.kyc_level OR NEW.verification_status <> OLD.verification_status THEN
    INSERT INTO kafka_outbox (aggregate_id, aggregate_type, event_type, payload, kafka_topic)
    VALUES (
      NEW.user_id,
      'KYC',
      'KYC_LEVEL_CHANGED',
      jsonb_build_object(
        'user_id',            NEW.user_id,
        'kyc_level',          NEW.kyc_level,
        'verification_status',NEW.verification_status,
        'monthly_limit',      NEW.monthly_limit
      ),
      'kyc-events'
    );
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_kyc_sync_keycloak
  AFTER UPDATE ON kyc_profiles
  FOR EACH ROW EXECUTE FUNCTION sync_kyc_level_to_keycloak();

-- =============================================================
--  VUES utilitaires
-- =============================================================

-- Vue réconciliation mensuelle par utilisateur
CREATE VIEW v_user_monthly_summary AS
SELECT
  u.id                              AS user_id,
  u.keycloak_id,
  u.email,
  kp.kyc_level,
  kp.monthly_limit,
  kp.monthly_used,
  kp.monthly_limit - kp.monthly_used AS remaining_limit,
  COUNT(t.id)                       AS tx_count_this_month,
  COALESCE(SUM(t.source_amount), 0) AS total_sent_this_month
FROM users u
LEFT JOIN kyc_profiles kp ON kp.user_id = u.id
LEFT JOIN transactions t ON t.user_id = u.id
  AND t.status = 'DELIVERED'
  AND DATE_TRUNC('month', t.completed_at) = DATE_TRUNC('month', NOW())
GROUP BY u.id, u.keycloak_id, u.email, kp.kyc_level, kp.monthly_limit, kp.monthly_used;

-- Vue sessions actives (backoffice monitoring)
CREATE VIEW v_active_sessions AS
SELECT
  s.id,
  s.keycloak_session_id,
  s.client,
  s.ip_address,
  s.started_at,
  s.last_seen_at,
  u.email,
  u.keycloak_id,
  kp.kyc_level
FROM user_sessions s
JOIN users u ON u.id = s.user_id
LEFT JOIN kyc_profiles kp ON kp.user_id = s.user_id
WHERE s.is_active = TRUE
ORDER BY s.last_seen_at DESC;

-- Vue corridor performance (backoffice)
CREATE VIEW v_corridor_stats AS
SELECT
  source_currency,
  dest_currency,
  COUNT(*)                           AS total_transactions,
  SUM(source_amount)                 AS total_volume_eur,
  AVG(source_amount)                 AS avg_amount,
  SUM(fee_amount)                    AS total_fees,
  COUNT(*) FILTER (WHERE status='DELIVERED') AS successful,
  COUNT(*) FILTER (WHERE status='FAILED')    AS failed,
  ROUND(
    100.0 * COUNT(*) FILTER (WHERE status='DELIVERED') / NULLIF(COUNT(*),0), 2
  )                                  AS success_rate_pct
FROM transactions
WHERE created_at >= NOW() - INTERVAL '30 days'
GROUP BY source_currency, dest_currency;

-- Vue audit par agent backoffice (conformité)
CREATE VIEW v_admin_activity AS
SELECT
  a.id,
  a.occurred_at,
  a.event_type,
  a.entity_type,
  a.entity_id,
  au.email         AS admin_email,
  au.full_name     AS admin_name,
  au.role          AS admin_role,
  a.keycloak_session_id,
  a.ip_address,
  a.before_state,
  a.after_state
FROM audit_events a
JOIN admin_users au ON au.id = a.admin_id
ORDER BY a.occurred_at DESC;
