package com.zalamtech.diama.api.util;

import io.quarkus.panache.common.Sort;

/**
 * Parse le paramètre de tri conventionnel des endpoints backoffice
 * ({@code ?sort=champ,asc|desc}, ex. {@code createdAt,desc}).
 *
 * <p>Le champ est un nom de propriété de l'entité Panache ciblée (pas de
 * traduction snake_case → camelCase : l'API backoffice s'aligne directement
 * sur les noms de champs Java).</p>
 */
public final class SortParam {

    private SortParam() {
    }

    /** @param defaultField champ utilisé (tri descendant) si {@code sortParam} est absent/vide */
    public static Sort parse(String sortParam, String defaultField) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(defaultField).descending();
        }
        final String[] parts = sortParam.split(",", 2);
        final String field = parts[0].trim();
        final boolean ascending = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim());
        return ascending ? Sort.by(field).ascending() : Sort.by(field).descending();
    }
}
