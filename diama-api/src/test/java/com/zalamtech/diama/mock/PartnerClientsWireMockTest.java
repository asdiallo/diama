package com.zalamtech.diama.mock;

import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.kyc.DiditDocument;
import com.zalamtech.diama.domain.kyc.DiditGateway;
import com.zalamtech.diama.domain.kyc.DiditSession;
import com.zalamtech.diama.domain.transaction.ThunesTransferGateway;
import com.zalamtech.diama.domain.transaction.ThunesTransferResult;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exerce les adaptateurs REST réels ({@code ThunesClient}, {@code DiditClient})
 * contre des stubs WireMock : sérialisation des requêtes, désérialisation des
 * réponses et mapping transport → domaine — ce que les mocks de port des tests
 * de flux ne couvrent pas.
 */
@QuarkusTest
@QuarkusTestResource(value = PartnerApisWireMockResource.class, restrictToAnnotatedClass = true)
class PartnerClientsWireMockTest {

    @Inject
    ThunesTransferGateway thunes;

    @Inject
    DiditGateway didit;

    @Test
    void thunes_payers_are_fetched_and_mapped_to_domain() {
        final List<ThunesPayer> payers = thunes.getPayers("GN");

        assertEquals(1, payers.size());
        final ThunesPayer payer = payers.get(0);
        assertEquals(ThunesClientMock.PAYER_ID, payer.getId());
        assertEquals("Orange Money Guinée", payer.getName());
        assertEquals("GN", payer.getCountryIso());
        assertEquals("GNF", payer.getCurrency());
        assertEquals("MobileWallet", payer.getServiceType());
    }

    @Test
    void thunes_transaction_lookup_returns_status() {
        final ThunesTransferResult result = thunes.fetchTransaction(ThunesClientMock.TRANSACTION_ID);

        assertEquals(ThunesClientMock.TRANSACTION_ID, result.thunesTransactionId());
        assertEquals("40000", result.status());
    }

    @Test
    void didit_session_is_created_and_mapped() {
        final DiditSession session = didit.createSession("wf-level-1", "user-ext-1");

        assertEquals(DiditClientMock.SESSION_ID, session.sessionId());
        assertEquals(DiditClientMock.SESSION_TOKEN, session.sessionToken());
        assertEquals(DiditClientMock.SESSION_URL, session.url());
    }

    @Test
    void didit_decision_documents_are_parsed() {
        final List<DiditDocument> documents = didit.getDocuments(DiditClientMock.SESSION_ID);

        assertEquals(1, documents.size());
        assertEquals("id_verification_1", documents.get(0).nodeId());
        assertEquals("Passport", documents.get(0).documentType());
        assertEquals("FRA", documents.get(0).issuingState());
        assertEquals("Approved", documents.get(0).status());
    }
}
