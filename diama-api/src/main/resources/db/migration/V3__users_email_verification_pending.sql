-- Comptes créés par POST /api/v1/auth/register dont l'email n'est pas encore
-- confirmé. Sert de liste de travail à la purge des inscriptions abandonnées :
-- les utilisateurs existants restent à FALSE et ne sont donc jamais purgés.
ALTER TABLE users
  ADD COLUMN email_verification_pending BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_users_email_verification_pending
  ON users (created_at)
  WHERE email_verification_pending;
