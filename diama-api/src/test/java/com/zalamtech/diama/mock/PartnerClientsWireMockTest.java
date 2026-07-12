package com.zalamtech.diama.mock;

import com.zalamtech.diama.common.dto.SumsubReviewResult;
import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.kyc.SumsubApplicant;
import com.zalamtech.diama.domain.kyc.SumsubGateway;
import com.zalamtech.diama.domain.transaction.ThunesTransferGateway;
import com.zalamtech.diama.domain.transaction.ThunesTransferResult;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exerce les adaptateurs REST réels ({@code ThunesClient}, {@code SumsubClient})
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
    SumsubGateway sumsub;

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
    void sumsub_applicant_creation_and_token() {
        final SumsubApplicant applicant = sumsub.createApplicant("user-ext-1", "basic-kyc-level");
        assertEquals(SumsubClientMock.APPLICANT_ID, applicant.applicantId());

        assertEquals(SumsubClientMock.SDK_TOKEN, sumsub.generateAccessToken(applicant.applicantId()));
    }

    @Test
    void sumsub_review_status_is_parsed() {
        final SumsubReviewResult status = sumsub.getApplicantStatus(SumsubClientMock.APPLICANT_ID);

        assertEquals("completed", status.reviewStatus());
        assertEquals("GREEN", status.reviewAnswer());
    }
}
