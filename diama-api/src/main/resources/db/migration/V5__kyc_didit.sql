-- Remplacement de Sumsub par Didit comme fournisseur KYC.
-- Les identifiants Sumsub ne servent plus : colonnes renommées et vidées.

-- kyc_profiles : applicant Sumsub (un par utilisateur) → session Didit
-- (une par vérification, la dernière lancée est conservée).
ALTER TABLE kyc_profiles RENAME COLUMN sumsub_applicant_id TO didit_session_id;
ALTER TABLE kyc_profiles RENAME CONSTRAINT kyc_profiles_sumsub_applicant_id_key TO kyc_profiles_didit_session_id_key;
ALTER INDEX idx_kyc_sumsub RENAME TO idx_kyc_didit_session;
UPDATE kyc_profiles SET didit_session_id = NULL;

-- kyc_documents : "<session_id>:<node_id>" (le node_id n'est unique qu'au sein
-- d'une session).
ALTER TABLE kyc_documents RENAME COLUMN sumsub_doc_id TO didit_document_id;
ALTER TABLE kyc_documents ALTER COLUMN didit_document_id TYPE VARCHAR(150);
UPDATE kyc_documents SET didit_document_id = NULL;

-- Niveau 0 = parcours jamais abouti : KycService.initKyc crée le profil à 0,
-- ce que la contrainte initiale (1..4) refusait.
ALTER TABLE kyc_profiles DROP CONSTRAINT kyc_profiles_kyc_level_check;
ALTER TABLE kyc_profiles ADD CONSTRAINT kyc_profiles_kyc_level_check CHECK (kyc_level BETWEEN 0 AND 4);
