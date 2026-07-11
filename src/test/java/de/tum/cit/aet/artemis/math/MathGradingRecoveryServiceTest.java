package de.tum.cit.aet.artemis.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import de.tum.cit.aet.artemis.math.domain.MathGradingJob;
import de.tum.cit.aet.artemis.math.domain.MathGradingJobStatus;
import de.tum.cit.aet.artemis.math.domain.MathGradingPhase;
import de.tum.cit.aet.artemis.math.grader.GradingSpeed;
import de.tum.cit.aet.artemis.math.repository.MathGradingJobRepository;
import de.tum.cit.aet.artemis.math.service.MathGradingDispatcher;
import de.tum.cit.aet.artemis.math.service.MathGradingRecoveryService;

/**
 * Unit tests for the crash-recovery reconciliation of stuck {@link MathGradingJob}s. Verifies the three outcomes:
 * a stuck job under the attempt cap is claimed and re-dispatched, a job past the cap is given up (REVIEW), and a
 * job lost to a concurrent claim (optimistic-lock failure) is skipped without re-dispatch.
 */
@ExtendWith(MockitoExtension.class)
class MathGradingRecoveryServiceTest {

    @Mock
    private MathGradingJobRepository mathGradingJobRepository;

    @Mock
    private MathGradingDispatcher mathGradingDispatcher;

    @InjectMocks
    private MathGradingRecoveryService recoveryService;

    private static MathGradingJob stuckJob(int attempts) {
        MathGradingJob job = new MathGradingJob();
        job.setId(1L);
        job.setSubmissionId(7L);
        job.setExerciseId(3L);
        job.setPhase(MathGradingPhase.PRELIMINARY);
        job.setLane(GradingSpeed.FAST);
        job.setStatus(MathGradingJobStatus.PENDING);
        job.setAttempts(attempts);
        job.setCreatedDate(Instant.now().minusSeconds(3600));
        return job;
    }

    @Test
    void claimsAndRedispatchesStuckJobUnderAttemptCap() {
        MathGradingJob job = stuckJob(1);
        when(mathGradingJobRepository.findStuck(eq(MathGradingJobStatus.PENDING), any(), any())).thenReturn(List.of(job));
        when(mathGradingJobRepository.save(job)).thenReturn(job);

        recoveryService.recoverStuckJobs();

        assertThat(job.getAttempts()).isEqualTo(2);
        assertThat(job.getStartedDate()).isNotNull();
        verify(mathGradingDispatcher).redispatch(job);
    }

    @Test
    void givesUpJobPastAttemptCap() {
        MathGradingJob job = stuckJob(3);
        when(mathGradingJobRepository.findStuck(eq(MathGradingJobStatus.PENDING), any(), any())).thenReturn(List.of(job));
        when(mathGradingJobRepository.save(job)).thenReturn(job);

        recoveryService.recoverStuckJobs();

        assertThat(job.getStatus()).isEqualTo(MathGradingJobStatus.REVIEW);
        assertThat(job.getFinishedDate()).isNotNull();
        verify(mathGradingDispatcher, never()).redispatch(any());
    }

    @Test
    void skipsJobClaimedConcurrentlyByAnotherNode() {
        MathGradingJob job = stuckJob(1);
        when(mathGradingJobRepository.findStuck(eq(MathGradingJobStatus.PENDING), any(), any())).thenReturn(List.of(job));
        when(mathGradingJobRepository.save(job)).thenThrow(new ObjectOptimisticLockingFailureException(MathGradingJob.class, 1L));

        recoveryService.recoverStuckJobs();

        verify(mathGradingDispatcher, never()).redispatch(any());
    }
}
