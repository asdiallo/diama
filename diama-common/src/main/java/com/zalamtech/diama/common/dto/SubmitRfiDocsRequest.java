package com.zalamtech.diama.common.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * Réponse backoffice à une RFI Thunes : documents (encodés base64) et/ou
 * informations complémentaires demandées.
 *
 * @param documents documents justificatifs à soumettre
 * @param info      informations complémentaires demandées (ex. {@code {"sender.date_of_birth": "1990-01-01"}})
 */
public record SubmitRfiDocsRequest(
        @Valid
        List<RfiDocumentUpload> documents,
        Map<String, String> info
) {

    /**
     * @param docType        type de document (ex. {@code identification_documents})
     * @param mimeType       type MIME du contenu
     * @param contentBase64  contenu du document, encodé base64
     */
    public record RfiDocumentUpload(
            @NotBlank String docType,
            @NotBlank String mimeType,
            @NotBlank String contentBase64
    ) {
    }
}
