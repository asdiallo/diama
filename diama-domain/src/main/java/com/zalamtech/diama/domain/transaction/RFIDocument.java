package com.zalamtech.diama.domain.transaction;

/**
 * Document soumis par le backoffice en réponse à une RFI Thunes.
 *
 * @param docType  type de document (ex. {@code identification_documents})
 * @param mimeType type MIME du contenu
 * @param content  contenu binaire du document
 */
public record RFIDocument(
        String docType,
        String mimeType,
        byte[] content
) {
}
