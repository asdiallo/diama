package com.zalamtech.diama.domain.transaction;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.zalamtech.diama.domain.transaction.TxStatus.AML_CLEARED;
import static com.zalamtech.diama.domain.transaction.TxStatus.CANCELLED;
import static com.zalamtech.diama.domain.transaction.TxStatus.CONFIRMED;
import static com.zalamtech.diama.domain.transaction.TxStatus.DELIVERED;
import static com.zalamtech.diama.domain.transaction.TxStatus.FAILED;
import static com.zalamtech.diama.domain.transaction.TxStatus.INITIATED;
import static com.zalamtech.diama.domain.transaction.TxStatus.PROCESSING;
import static com.zalamtech.diama.domain.transaction.TxStatus.REFUNDED;
import static com.zalamtech.diama.domain.transaction.TxStatus.UNDER_REVIEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Matrice de transitions de la FSM : la totalité des 81 paires
 * {@code from → to} est vérifiée contre la spécification — toute transition
 * hors matrice est interdite (test exhaustif, pur, sans conteneur).
 */
class TransactionFSMTest {

    /** Spécification de référence (miroir volontaire de la javadoc de TransactionFSM). */
    private static final Map<TxStatus, Set<TxStatus>> SPEC = new EnumMap<>(TxStatus.class);

    static {
        SPEC.put(INITIATED, Set.of(AML_CLEARED, FAILED, CANCELLED));
        SPEC.put(AML_CLEARED, Set.of(CONFIRMED, FAILED));
        SPEC.put(CONFIRMED, Set.of(UNDER_REVIEW, PROCESSING));
        SPEC.put(UNDER_REVIEW, Set.of(PROCESSING, FAILED, CANCELLED));
        SPEC.put(PROCESSING, Set.of(DELIVERED, FAILED));
        SPEC.put(DELIVERED, Set.of());
        SPEC.put(FAILED, Set.of(REFUNDED));
        SPEC.put(REFUNDED, Set.of());
        SPEC.put(CANCELLED, Set.of());
    }

    @Test
    void every_pair_matches_specification() {
        for (TxStatus from : TxStatus.values()) {
            for (TxStatus to : TxStatus.values()) {
                final boolean expected = SPEC.get(from).contains(to);
                assertEquals(expected, TransactionFSM.isAllowed(from, to),
                        "%s → %s devrait être %s".formatted(from, to, expected ? "autorisée" : "interdite"));
            }
        }
    }

    @Test
    void nominal_happy_path_is_fully_allowed() {
        assertTrue(TransactionFSM.isAllowed(INITIATED, AML_CLEARED));
        assertTrue(TransactionFSM.isAllowed(AML_CLEARED, CONFIRMED));
        assertTrue(TransactionFSM.isAllowed(CONFIRMED, PROCESSING));
        assertTrue(TransactionFSM.isAllowed(PROCESSING, DELIVERED));
    }

    @Test
    void compliance_detour_via_under_review_is_allowed() {
        assertTrue(TransactionFSM.isAllowed(CONFIRMED, UNDER_REVIEW));
        assertTrue(TransactionFSM.isAllowed(UNDER_REVIEW, PROCESSING));
        assertTrue(TransactionFSM.isAllowed(UNDER_REVIEW, FAILED));
        assertTrue(TransactionFSM.isAllowed(UNDER_REVIEW, CANCELLED));
    }

    @Test
    void terminal_states_have_no_outgoing_transition_except_failed_to_refunded() {
        for (TxStatus to : TxStatus.values()) {
            assertFalse(TransactionFSM.isAllowed(DELIVERED, to), "DELIVERED est terminal");
            assertFalse(TransactionFSM.isAllowed(REFUNDED, to), "REFUNDED est terminal");
            assertFalse(TransactionFSM.isAllowed(CANCELLED, to), "CANCELLED est terminal");
        }
        assertTrue(TransactionFSM.isAllowed(FAILED, REFUNDED), "seul FAILED → REFUNDED est permis");
    }

    @Test
    void no_backward_or_skipping_transitions() {
        assertFalse(TransactionFSM.isAllowed(INITIATED, CONFIRMED), "saut AML interdit");
        assertFalse(TransactionFSM.isAllowed(INITIATED, DELIVERED), "saut direct interdit");
        assertFalse(TransactionFSM.isAllowed(PROCESSING, INITIATED), "retour arrière interdit");
        assertFalse(TransactionFSM.isAllowed(CONFIRMED, AML_CLEARED), "retour arrière interdit");
        assertFalse(TransactionFSM.isAllowed(PROCESSING, CANCELLED), "annulation trop tardive");
    }

    @Test
    void null_states_are_never_allowed() {
        assertFalse(TransactionFSM.isAllowed(null, DELIVERED));
        assertFalse(TransactionFSM.isAllowed(INITIATED, null));
        assertFalse(TransactionFSM.isAllowed(null, null));
    }

    @Test
    void self_transitions_are_forbidden() {
        for (TxStatus status : TxStatus.values()) {
            assertFalse(TransactionFSM.isAllowed(status, status),
                    status + " → " + status + " (idempotence gérée en amont, pas par la FSM)");
        }
    }
}
