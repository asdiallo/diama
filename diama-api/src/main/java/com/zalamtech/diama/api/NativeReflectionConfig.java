package com.zalamtech.diama.api;

import com.zalamtech.diama.common.dto.*;
import com.zalamtech.diama.domain.audit.NotificationChannel;
import com.zalamtech.diama.domain.exchange.ExchangeRate;
import com.zalamtech.diama.domain.transaction.TxStatus;
import com.zalamtech.diama.infra.kafka.AuditEventMessage;
import com.zalamtech.diama.infra.kafka.KycEvent;
import com.zalamtech.diama.infra.kafka.NotificationEvent;
import com.zalamtech.diama.infra.kafka.RateUpdatedEvent;
import com.zalamtech.diama.infra.kafka.SwanWebhookEnvelope;
import com.zalamtech.diama.infra.kafka.ThunesCallbackEvent;
import com.zalamtech.diama.infra.kafka.TransferEvent;
import com.zalamtech.diama.infra.thunes.ThunesCallbackPayload;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Classes (dé)sérialisées par Jackson hors des endpoints REST — payloads d'audit
 * ({@code valueToTree}), messages Kafka, cache Redis, webhooks lus en corps brut.
 * Quarkus n'enregistre automatiquement que les types d'entrée/sortie REST : en
 * natif, les autres doivent être déclarés ici, sinon Jackson ne voit aucune propriété.
 */
@RegisterForReflection(targets = {
                // DTO partagés (diama-common)
                AccountRegistrationDto.class,
                ActiveSessionDto.class,
                AdminFeeDto.class,
                AdminKycReviewDto.class,
                AdminRateDto.class,
                AdminTransactionDto.class,
                AdminUserDetailDto.class,
                AdminUserListItemDto.class,
                AuditContext.class,
                AuditEventDto.class,
                BeneficiaryDto.class,
                CorridorStatsDto.class,
                CreateBeneficiaryDto.class,
                CreateFeeTierRequest.class,
                CreateTransferRequest.class,
                DashboardSummaryDto.class,
                DeviceRegistrationDto.class,
                DiditWebhookPayload.class,
                ForceStatusRequest.class,
                HourlyVolumeDto.class,
                KycDocumentDto.class,
                KycInitDto.class,
                KycStatusDto.class,
                LimitCheckResult.class,
                OrphanedFund.class,
                Page.class,
                PayerDto.class,
                QuotationDto.class,
                QuotationRequest.class,
                RateDto.class,
                RegistrationResultDto.class,
                ResendVerificationDto.class,
                RfiPendingDto.class,
                SubmitRfiDocsRequest.class,
                SwanAccount.class,
                SwanAccountRequest.class,
                SwanTransfer.class,
                SwanTransferRequest.class,
                SwanWebhookEvent.class,
                TransactionDetailDto.class,
                TransactionHistoryEntryDto.class,
                TransferDto.class,
                TransferStatusDto.class,
                UpdateSpreadRequest.class,
                UpdateUserStatusRequest.class,
                UserProfileDto.class,
                UserRegistrationDto.class,
                UserUpdateDto.class,
                // Messages Kafka
                AuditEventMessage.class,
                KycEvent.class,
                NotificationEvent.class,
                RateUpdatedEvent.class,
                SwanWebhookEnvelope.class,
                ThunesCallbackEvent.class,
                TransferEvent.class,
                // Webhook Thunes, cache Redis et enums associés
                ThunesCallbackPayload.class,
                ExchangeRate.class,
                NotificationChannel.class,
                TxStatus.class,
}, registerFullHierarchy = true)
public class NativeReflectionConfig {
}
