-- Compteur de tentatives d'envoi, mis à jour par NotificationConsumer sur échec.
ALTER TABLE notifications
  ADD COLUMN retry_count SMALLINT NOT NULL DEFAULT 0;
