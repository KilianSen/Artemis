package de.tum.cit.aet.artemis.math;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.math.domain.GoalMode;
import de.tum.cit.aet.artemis.math.domain.InductionDatatype;
import de.tum.cit.aet.artemis.math.domain.LayoutCategory;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.BlockDefinitionDTO;
import de.tum.cit.aet.artemis.math.dto.MathExerciseDTO;
import de.tum.cit.aet.artemis.math.dto.MathProblemDTO;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;
import de.tum.cit.aet.artemis.math.util.MathExerciseFactory;
import de.tum.cit.aet.artemis.math.util.MathExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class MathExerciseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "mathexercise";

    @Autowired
    private MathExerciseRepository mathExerciseRepository;

    @Autowired
    private MathSubmissionRepository mathSubmissionRepository;

    @Autowired
    private MathExerciseUtilService mathExerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private Course course;

    private MathExercise exercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = mathExerciseUtilService.addCourseWithMathExercise();
        exercise = (MathExercise) course.getExercises().iterator().next();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_asInstructor_returnsCreated() throws Exception {
        MathExerciseDTO newExercise = MathExerciseFactory.generateMathExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises", newExercise, MathExerciseDTO.class, HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.problemStatement()).isEqualTo(newExercise.problemStatement());
        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().getFirst().sourceExpression()).isNotNull();
        assertThat(result.problems().getFirst().targetExpression()).isNotNull();
        // A linked communication channel is created on create, like every other exercise type (and math's own import path).
        assertThat(result.channelName()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMathExercise_asStudent_returnsForbidden() throws Exception {
        MathExerciseDTO newExercise = MathExerciseFactory.generateMathExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        request.postWithResponseBody("/api/math/math-exercises", newExercise, MathExerciseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createMathExercise_withExistingId_returnsBadRequest() throws Exception {
        MathExerciseDTO exerciseWithId = MathExerciseDTO.of(exercise);

        request.postWithResponseBody("/api/math/math-exercises", exerciseWithId, MathExerciseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathExercise_asTutor_returnsOk() throws Exception {
        MathExerciseDTO result = request.get("/api/math/math-exercises/" + exercise.getId(), HttpStatus.OK, MathExerciseDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(exercise.getId());
        assertThat(result.problemStatement()).isEqualTo(exercise.getProblemStatement());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMathExercise_asStudent_returnsForbidden() throws Exception {
        request.get("/api/math/math-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN, MathExerciseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getBlockRegistry_asStudent_returnsCatalogue() throws Exception {
        // Students need the block/rule catalogue to build a derivation during participation; the endpoint must be
        // student-accessible (it was previously editor-gated, which broke participation with a 403 + empty rule list).
        var blocks = request.getList("/api/math/block-registry", HttpStatus.OK, BlockDefinitionDTO.class);
        assertThat(blocks).isNotEmpty();
        assertThat(blocks).anySatisfy(block -> assertThat(block.rules()).isNotEmpty());
        // The named function-application term (Regate capability C1) is served with the FUNCTION_APP layout and its
        // single `args` slot, so the editor can render/round-trip multi-argument functions like fact_aux(x, n).
        assertThat(blocks).anySatisfy(block -> {
            assertThat(block.type()).isEqualTo("apply");
            assertThat(block.layoutCategory()).isEqualTo(LayoutCategory.FUNCTION_APP);
            assertThat(block.slots()).containsExactly("args");
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathExercisesForCourse_returnsList() throws Exception {
        var results = request.getList("/api/math/courses/" + course.getId() + "/math-exercises", HttpStatus.OK, MathExerciseDTO.class);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().id()).isEqualTo(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateMathExercise_asInstructor_returnsOk() throws Exception {
        exercise.setProblemStatement("Updated problem statement");
        MathExerciseDTO updateDTO = MathExerciseDTO.of(exercise);

        MathExerciseDTO result = request.putWithResponseBody("/api/math/math-exercises", updateDTO, MathExerciseDTO.class, HttpStatus.OK);

        assertThat(result.problemStatement()).isEqualTo("Updated problem statement");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteMathExercise_asInstructor_returnsOk() throws Exception {
        request.delete("/api/math/math-exercises/" + exercise.getId(), HttpStatus.OK);

        assertThat(mathExerciseRepository.findById(exercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteMathExercise_asStudent_returnsForbidden() throws Exception {
        request.delete("/api/math/math-exercises/" + exercise.getId(), HttpStatus.FORBIDDEN);
        // the exercise must still exist after a forbidden delete
        assertThat(mathExerciseRepository.findById(exercise.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void reEvaluateAndUpdateMathExercise_asInstructor_returnsOk() throws Exception {
        exercise.setProblemStatement("Re-evaluated problem statement");
        MathExerciseDTO updateDTO = MathExerciseDTO.of(exercise);

        MathExerciseDTO result = request.putWithResponseBody("/api/math/math-exercises/" + exercise.getId() + "/re-evaluate", updateDTO, MathExerciseDTO.class, HttpStatus.OK);

        assertThat(result.problemStatement()).isEqualTo("Re-evaluated problem statement");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void reEvaluateAndUpdateMathExercise_idMismatch_returnsBadRequest() throws Exception {
        MathExerciseDTO updateDTO = MathExerciseDTO.of(exercise);

        request.putWithResponseBody("/api/math/math-exercises/" + (exercise.getId() + 1) + "/re-evaluate", updateDTO, MathExerciseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExercise_asInstructor_returnsCreated() throws Exception {
        MathExerciseDTO importTarget = MathExerciseFactory.generateMathExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises/import?sourceExerciseId=" + exercise.getId(), importTarget, MathExerciseDTO.class,
                HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotEqualTo(exercise.getId());
        assertThat(result.problemStatement()).isEqualTo(importTarget.problemStatement());
        // the imported exercise must preserve the problem configuration sent in the import payload
        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().getFirst().sourceExpression()).isNotNull();
        assertThat(result.problems().getFirst().targetExpression()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExercise_preservesManualDerivation() throws Exception {
        MathProblemDTO problemDTO = new MathProblemDTO(null, "Problem 1", 10.0, MathExerciseFactory.sampleSource(), MathExerciseFactory.sampleTarget(), null, null, null, null,
                false, false, false, true, true, null, null, null);
        MathExerciseDTO importTarget = new MathExerciseDTO(null, "Imported Math Exercise", null, "Prove that 0 + x = x.", null, null, 10.0, 0.0,
                IncludedInOverallScore.INCLUDED_COMPLETELY, false, false, false, false, null, null, ZonedDateTime.now().minusDays(1), null, ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), null, course.getId(), null, List.of(problemDTO), null);

        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises/import?sourceExerciseId=" + exercise.getId(), importTarget, MathExerciseDTO.class,
                HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().getFirst().manualDerivation()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExercise_preservesInductionConfiguration() throws Exception {
        // The induction variable and datatype are part of an INDUCTION problem's grader configuration. If the import drops
        // them, a LIST-induction problem silently degrades into a variable-less ℕ-induction one (the column defaults to NAT).
        MathProblemDTO problemDTO = new MathProblemDTO(null, "Induction Problem", 10.0, null, null, MathNodes.eq(MathNodes.var("xs"), MathNodes.var("xs")), GoalMode.INDUCTION,
                null, null, false, false, false, true, false, null, "xs", InductionDatatype.LIST);
        MathExerciseDTO importTarget = new MathExerciseDTO(null, "Imported Induction Exercise", null, "Prove the list property by induction.", null, null, 10.0, 0.0,
                IncludedInOverallScore.INCLUDED_COMPLETELY, false, false, false, false, null, null, ZonedDateTime.now().minusDays(1), null, ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), null, course.getId(), null, List.of(problemDTO), null);

        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises/import?sourceExerciseId=" + exercise.getId(), importTarget, MathExerciseDTO.class,
                HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().getFirst().goalMode()).isEqualTo(GoalMode.INDUCTION);
        assertThat(result.problems().getFirst().inductionVariable()).isEqualTo("xs");
        assertThat(result.problems().getFirst().inductionDatatype()).isEqualTo(InductionDatatype.LIST);

        // also assert on the persisted entity, not only on the response projection
        MathExercise persisted = mathExerciseRepository.findByIdWithCategoriesAndProblems(result.id()).orElseThrow();
        assertThat(persisted.getProblems()).hasSize(1);
        assertThat(persisted.getProblems().getFirst().getInductionVariable()).isEqualTo("xs");
        assertThat(persisted.getProblems().getFirst().getInductionDatatype()).isEqualTo(InductionDatatype.LIST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExercise_copiesExampleSubmissionInsteadOfSharing() throws Exception {
        ExampleSubmission originalExampleSubmission = mathExerciseUtilService.addExampleSubmissionToMathExercise(exercise, "example work");
        long originalSubmissionId = originalExampleSubmission.getSubmission().getId();

        MathExerciseDTO importTarget = MathExerciseFactory.generateMathExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        // Import must succeed: sharing the template's submission would violate the unique @OneToOne constraint on ExampleSubmission.submission.
        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises/import?sourceExerciseId=" + exercise.getId(), importTarget, MathExerciseDTO.class,
                HttpStatus.CREATED);

        MathExercise imported = mathExerciseRepository.findByIdWithCourseAndExampleSubmissions(result.id()).orElseThrow();
        assertThat(imported.getExampleSubmissions()).hasSize(1);
        Submission copiedSubmission = imported.getExampleSubmissions().iterator().next().getSubmission();
        assertThat(copiedSubmission).isNotNull();
        // The copy must be a distinct submission, not the shared template one, so deleting either exercise cannot cascade-remove the other's data.
        assertThat(copiedSubmission.getId()).isNotEqualTo(originalSubmissionId);
        // The answer's derivation steps must be deep-copied onto the new submission (not shared with the template's).
        MathSubmission reloadedCopy = mathSubmissionRepository.findByIdWithAnswersAndResults(copiedSubmission.getId()).orElseThrow();
        assertThat(reloadedCopy.getAnswers()).hasSize(1);
        assertThat(reloadedCopy.getAnswers().iterator().next().getSteps()).hasSize(1);
        assertThat(reloadedCopy.getAnswers().iterator().next().getSteps().iterator().next().getAppliedRuleId()).isEqualTo("example work");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importMathExercise_copiesExampleSubmissionAssessment() throws Exception {
        ExampleSubmission originalExampleSubmission = mathExerciseUtilService.addExampleSubmissionWithAssessmentToMathExercise(exercise, "assessed work", 7.0, "good derivation");
        long originalResultId = originalExampleSubmission.getSubmission().getResults().getFirst().getId();

        MathExerciseDTO importTarget = MathExerciseFactory.generateMathExerciseDTO(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), course);

        MathExerciseDTO result = request.postWithResponseBody("/api/math/math-exercises/import?sourceExerciseId=" + exercise.getId(), importTarget, MathExerciseDTO.class,
                HttpStatus.CREATED);

        MathExercise imported = mathExerciseRepository.findByIdWithCourseAndExampleSubmissions(result.id()).orElseThrow();
        long copiedSubmissionId = imported.getExampleSubmissions().iterator().next().getSubmission().getId();
        // Reload the copied submission's assessment via the targeted query (the exercise query no longer eagerly fetches results).
        MathSubmission copiedSubmission = mathSubmissionRepository.findByIdWithResultsAndFeedbacksAndAssessor(copiedSubmissionId).orElseThrow();
        // The assessment must be copied as exactly one distinct result with its feedback and score.
        assertThat(copiedSubmission.getResults()).hasSize(1);
        Result copiedResult = copiedSubmission.getResults().getFirst();
        assertThat(copiedResult.getId()).isNotEqualTo(originalResultId);
        assertThat(copiedResult.getScore()).isEqualTo(7.0);
        assertThat(copiedResult.getFeedbacks()).hasSize(1);
        assertThat(copiedResult.getFeedbacks().iterator().next().getDetailText()).isEqualTo("good derivation");
    }
}
