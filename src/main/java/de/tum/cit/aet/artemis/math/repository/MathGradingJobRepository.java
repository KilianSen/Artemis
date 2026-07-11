package de.tum.cit.aet.artemis.math.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathGradingJob;
import de.tum.cit.aet.artemis.math.domain.MathGradingJobStatus;
import de.tum.cit.aet.artemis.math.domain.MathGradingPhase;

/**
 * Spring Data JPA repository for {@link MathGradingJob}, the durable record of an asynchronous remote grading pass.
 */
@Conditional(MathEnabled.class)
@Lazy
@Repository
public interface MathGradingJobRepository extends ArtemisJpaRepository<MathGradingJob, Long> {

    Optional<MathGradingJob> findBySubmissionIdAndPhase(Long submissionId, MathGradingPhase phase);

    /**
     * Finds jobs still {@code PENDING} that are stuck: either never picked up (no {@code startedDate}) since before
     * {@code pickupCutoff}, or in flight (a {@code startedDate}) since before {@code inFlightCutoff} — the grading node
     * most likely died. These are candidates for recovery re-dispatch.
     *
     * @param status         the status to match (typically {@code PENDING})
     * @param pickupCutoff   jobs created before this and never started are considered stuck
     * @param inFlightCutoff jobs started before this are considered stuck (their node likely crashed mid-grade)
     * @return the stuck jobs
     */
    @Query("""
            SELECT j FROM MathGradingJob j
            WHERE j.status = :status
                AND ((j.startedDate IS NULL AND j.createdDate < :pickupCutoff)
                    OR (j.startedDate IS NOT NULL AND j.startedDate < :inFlightCutoff))
            """)
    List<MathGradingJob> findStuck(@Param("status") MathGradingJobStatus status, @Param("pickupCutoff") Instant pickupCutoff, @Param("inFlightCutoff") Instant inFlightCutoff);
}
