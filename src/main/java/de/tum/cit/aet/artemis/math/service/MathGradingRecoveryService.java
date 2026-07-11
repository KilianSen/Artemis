package de.tum.cit.aet.artemis.math.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathGradingJob;
import de.tum.cit.aet.artemis.math.domain.MathGradingJobStatus;
import de.tum.cit.aet.artemis.math.repository.MathGradingJobRepository;

/**
 * Recovers durable {@link MathGradingJob}s left {@link MathGradingJobStatus#PENDING} after a crash, so an async remote
 * grade interrupted by a server restart is not lost and the submission never stays stuck without a result.
 * <p>
 * A job is considered stuck if it was never picked up shortly after being enqueued (its node died before the async task
 * ran) or if it has been in flight far longer than any grade should take (its node died mid-grade). Each stuck job is
 * claimed with an optimistic-lock guard — in a multi-node cluster every scheduling node runs this scan, but only one
 * wins the claim and re-dispatches; the others’ saves trip the version check and are skipped. After
 * {@link #MAX_ATTEMPTS} dispatches a job is given up and left for manual review.
 */
@Lazy
@Service
@Profile(PROFILE_SCHEDULING)
@Conditional(MathEnabled.class)
public class MathGradingRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(MathGradingRecoveryService.class);

    /** How long after enqueue a still-unstarted job is treated as never-picked-up (node crashed before the async task ran). */
    private static final Duration PICKUP_GRACE = Duration.ofMinutes(2);

    /** How long an in-flight (started) job may run before its node is presumed dead and the job is re-dispatchable. */
    private static final Duration STUCK_DEADLINE = Duration.ofMinutes(5);

    /** Total dispatch attempts (initial + recovery) before a job is given up and left for manual review. */
    private static final int MAX_ATTEMPTS = 3;

    private final MathGradingJobRepository mathGradingJobRepository;

    private final MathGradingDispatcher mathGradingDispatcher;

    public MathGradingRecoveryService(MathGradingJobRepository mathGradingJobRepository, MathGradingDispatcher mathGradingDispatcher) {
        this.mathGradingJobRepository = mathGradingJobRepository;
        this.mathGradingDispatcher = mathGradingDispatcher;
    }

    /** Periodically finds stuck PENDING grading jobs and re-dispatches (or gives up on) them. */
    @Scheduled(fixedDelayString = "${artemis.math.grading-recovery-interval-ms:120000}", initialDelayString = "${artemis.math.grading-recovery-initial-delay-ms:120000}")
    public void recoverStuckJobs() {
        Instant now = Instant.now();
        List<MathGradingJob> stuck = mathGradingJobRepository.findStuck(MathGradingJobStatus.PENDING, now.minus(PICKUP_GRACE), now.minus(STUCK_DEADLINE));
        if (stuck.isEmpty()) {
            return;
        }
        log.info("Math grading recovery: {} stuck job(s) to reconcile", stuck.size());
        for (MathGradingJob job : stuck) {
            recoverOne(job, now);
        }
    }

    private void recoverOne(MathGradingJob job, Instant now) {
        if (job.getAttempts() >= MAX_ATTEMPTS) {
            job.setStatus(MathGradingJobStatus.REVIEW);
            job.setFinishedDate(now);
            try {
                mathGradingJobRepository.save(job);
                log.warn("Math grading job {} exhausted {} attempts; left for manual review", job.getId(), MAX_ATTEMPTS);
            }
            catch (ObjectOptimisticLockingFailureException e) {
                log.debug("Math grading job {} concurrently updated while giving up; skipping", job.getId());
            }
            return;
        }
        // Claim the job: bump attempts + start time. The optimistic version makes this claim exclusive across nodes.
        job.setAttempts(job.getAttempts() + 1);
        job.setStartedDate(now);
        MathGradingJob claimed;
        try {
            claimed = mathGradingJobRepository.save(job);
        }
        catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Math grading job {} claimed by another node; skipping", job.getId());
            return;
        }
        log.info("Re-dispatching math grading job {} (submission {}, attempt {})", claimed.getId(), claimed.getSubmissionId(), claimed.getAttempts());
        mathGradingDispatcher.redispatch(claimed);
    }
}
