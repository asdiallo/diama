package com.zalamtech.diama.domain.dashboard;

import com.zalamtech.diama.common.dto.CorridorStatsDto;
import com.zalamtech.diama.common.dto.DashboardSummaryDto;
import com.zalamtech.diama.common.dto.HourlyVolumeDto;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import com.zalamtech.diama.domain.kyc.KycVerificationStatus;
import com.zalamtech.diama.domain.transaction.CorridorStatsRepository;
import com.zalamtech.diama.domain.transaction.RfiRequestRepository;
import com.zalamtech.diama.domain.transaction.RfiStatus;
import com.zalamtech.diama.domain.transaction.TransactionRepository;
import com.zalamtech.diama.domain.transaction.TxStatus;
import com.zalamtech.diama.domain.user.UserSessionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Agrégats du tableau de bord backoffice. */
@ApplicationScoped
public class AdminDashboardService {

    @Inject
    TransactionRepository transactions;

    @Inject
    RfiRequestRepository rfiRequests;

    @Inject
    KycProfileRepository kycProfiles;

    @Inject
    UserSessionRepository sessions;

    @Inject
    CorridorStatsRepository corridorStats;

    public DashboardSummaryDto summary() {
        final OffsetDateTime since = startOfTodayUtc();

        final long txCount = transactions.count("createdAt >= ?1", since);
        final BigDecimal txVolume = transactions.sumSourceAmountByCreatedAfter(since);

        final long delivered = transactions.count("status = ?1 and createdAt >= ?2", TxStatus.DELIVERED, since);
        final long failed = transactions.count("status = ?1 and createdAt >= ?2", TxStatus.FAILED, since);
        final long concluded = delivered + failed;
        final double successRate = concluded == 0 ? 0.0 : (double) delivered / concluded;

        final long pendingRfi = rfiRequests.count("status", RfiStatus.PENDING);
        final long pendingKyc = kycProfiles.count("verificationStatus", KycVerificationStatus.PENDING);
        final long activeSessions = sessions.count("isActive", true);
        final BigDecimal revenueToday = transactions.sumFeeAmountByStatusAndCreatedAfter(TxStatus.DELIVERED, since);

        return new DashboardSummaryDto(txCount, txVolume, successRate, pendingRfi, pendingKyc, activeSessions, revenueToday);
    }

    public List<CorridorStatsDto> corridors() {
        return corridorStats.findAll();
    }

    public List<HourlyVolumeDto> hourly() {
        final OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusHours(24);
        return transactions.hourlyVolumeSince(since).stream().map(AdminDashboardService::toHourlyDto).toList();
    }

    private static OffsetDateTime startOfTodayUtc() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private static HourlyVolumeDto toHourlyDto(Tuple row) {
        return new HourlyVolumeDto(
                row.get("hour", OffsetDateTime.class),
                row.get("tx_count", Number.class).longValue(),
                row.get("volume", BigDecimal.class));
    }
}
