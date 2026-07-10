package com.zalamtech.diama.common.dto;

import java.util.List;

/**
 * Enveloppe de pagination générique renvoyée par les endpoints listant des
 * ressources (ex. historique des transferts).
 *
 * @param content       éléments de la page courante
 * @param page          index de page (0-based)
 * @param size          taille de page demandée
 * @param totalElements nombre total d'éléments toutes pages confondues
 * @param totalPages    nombre total de pages
 * @param <T>           type des éléments
 */
public record Page<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /** Construit une page en dérivant {@code totalPages} de {@code totalElements} et {@code size}. */
    public static <T> Page<T> of(List<T> content, int page, int size, long totalElements) {
        final int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new Page<>(content, page, size, totalElements, totalPages);
    }
}
