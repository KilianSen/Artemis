package de.tum.cit.aet.artemis.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathGradingJob;
import de.tum.cit.aet.artemis.math.domain.MathGradingJobStatus;
import de.tum.cit.aet.artemis.math.domain.MathGradingPhase;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.dto.MathProblemAnswerDTO;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.repository.MathGradingJobRepository;
import de.tum.cit.aet.artemis.math.util.MathExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Full-stack live test of the <b>asynchronous remote-grading web layer</b>. Boots the whole Artemis server
 * context (Spring + Postgres + the {@code @Async} executor + the real {@link de.tum.cit.aet.artemis.math.regate.RegateClient})
 * and drives the exact production path a remote-graded submission takes:
 * <ol>
 * <li>the problem is routed to the {@code EGGREGATE} backend, so {@code MathSubmissionResource} takes the
 * {@code usesRemoteGrader} branch and dispatches grading to {@code MathGradingDispatcher.dispatch} — the
 * submit response returns <b>without</b> a result and a durable {@code MathGradingJob} is persisted as
 * {@code PENDING};</li>
 * <li>the async thread grades against the live eggregate backend, records the authoritative result, and
 * transitions the job to {@code COMPLETED};</li>
 * <li>polling the {@code math-editor} endpoint (as the client would, before the websocket push) eventually
 * sees the result.</li>
 * </ol>
 * Requires a running eggregate backend; inert unless {@code REGATE_LIVE_URL} is set (CI skips it, and its
 * URL is wired into {@code artemis.regate.eggregate.url} via {@link DynamicPropertySource}).
 */
@EnabledIfEnvironmentVariable(named = "REGATE_LIVE_URL", matches = ".+")
class MathRegateAsyncGradingLiveTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "mathregateasync";

    @DynamicPropertySource
    static void regateProperties(DynamicPropertyRegistry registry) {
        registry.add("artemis.regate.eggregate.url", () -> System.getenv("REGATE_LIVE_URL"));
    }

    @Autowired
    private MathExerciseUtilService mathExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private MathGradingJobRepository mathGradingJobRepository;

    private MathExercise exercise;

    private StudentParticipation participation;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        Course course = mathExerciseUtilService.addCourseWithMathExercise();
        exercise = (MathExercise) course.getExercises().iterator().next();
        // Route the (transformation) problem to the remote eggregate backend so the submit grades asynchronously.
        exercise.getProblems().getFirst().setGraderType(GraderType.EGGREGATE);
        mathExerciseUtilService.saveExercise(exercise);
        participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitRemoteGraded_dispatchesAsync_thenResultAppears() throws Exception {
        Long problemId = exercise.getProblems().getFirst().getId();
        // Problem source is 0 + x; applying add_zero_left at the root reaches the target x.
        var stepDTO = new MathSubmissionDTO.DerivationStepDTO(null, 0, "add_zero_left", List.of(), MathNodes.var("x"));
        var answerDTO = new MathProblemAnswerDTO(null, problemId, null, List.of(stepDTO), null, null, null, null, null);
        MathSubmissionDTO submissionDTO = new MathSubmissionDTO(null, true, null, null, null, List.of(answerDTO));

        // Submit returns immediately with NO result — grading was handed to the async executor (remote grader).
        MathSubmissionDTO submitted = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);
        assertThat(submitted.submitted()).isTrue();
        assertThat(submitted.results()).isNullOrEmpty();
        Long submissionId = submitted.id();

        // A durable grading job was enqueued for the preliminary pass.
        MathGradingJob job = mathGradingJobRepository.findBySubmissionIdAndPhase(submissionId, MathGradingPhase.PRELIMINARY).orElseThrow();
        assertThat(job.getStatus()).isIn(MathGradingJobStatus.PENDING, MathGradingJobStatus.COMPLETED);

        // Poll the editor endpoint (as the client would, before the websocket push) until the async grading records the result.
        // pollInSameThread() keeps the polling on the test thread so the @WithMockUser security context applies.
        await().pollInSameThread().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            MathSubmissionDTO polled = request.get("/api/math/participations/" + participation.getId() + "/math-editor", HttpStatus.OK, MathSubmissionDTO.class);
            assertThat(polled.results()).isNotEmpty();
            assertThat(polled.results().getFirst().score()).isEqualTo(100.0);
        });

        // The job settled to COMPLETED once the result was recorded.
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            MathGradingJob settled = mathGradingJobRepository.findBySubmissionIdAndPhase(submissionId, MathGradingPhase.PRELIMINARY).orElseThrow();
            assertThat(settled.getStatus()).isEqualTo(MathGradingJobStatus.COMPLETED);
        });
    }
}
