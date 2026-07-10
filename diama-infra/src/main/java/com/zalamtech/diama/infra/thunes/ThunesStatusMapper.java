package com.zalamtech.diama.infra.thunes;

import com.zalamtech.diama.domain.transaction.TxStatus;

/** Traduit les codes de statut numériques Thunes (webhooks v2) en {@link TxStatus} interne. */
public final class ThunesStatusMapper {

    private ThunesStatusMapper() {
    }

    /** @return le {@link TxStatus} correspondant, ou {@code null} si le code est inconnu. */
    public static TxStatus fromThunesCode(String thunesStatusCode) {
        if (thunesStatusCode == null) {
            return null;
        }
        return switch (thunesStatusCode.trim()) {
            case "10000" -> TxStatus.CONFIRMED;
            case "20000" -> TxStatus.UNDER_REVIEW;
            case "40000" -> TxStatus.PROCESSING;
            case "70000" -> TxStatus.DELIVERED;
            case "80000", "90000" -> TxStatus.FAILED;
            default -> null;
        };
    }
}
