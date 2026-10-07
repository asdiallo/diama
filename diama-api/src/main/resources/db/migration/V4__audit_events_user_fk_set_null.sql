-- La trace d'audit doit survivre à la suppression de l'utilisateur (purge des
-- inscriptions non confirmées) : la ligne reste, user_id passe à NULL,
-- entity_id et keycloak_user_id conservent la référence.
ALTER TABLE audit_events
  DROP CONSTRAINT audit_events_user_id_fkey,
  ADD CONSTRAINT audit_events_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
