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
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.HintRequestDTO;
import de.tum.cit.aet.artemis.math.dto.HintSuggestionDTO;
import de.tum.cit.aet.artemis.math.dto.ManualResultRequestDTO;
import de.tum.cit.aet.artemis.math.dto.MathProblemAnswerDTO;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;
import de.tum.cit.aet.artemis.math.util.MathExerciseFactory;
import de.tum.cit.aet.artemis.math.util.MathExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class MathSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "mathsubmission";

    @Autowired
    private MathExerciseUtilService mathExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationTestRepository;

    @Autowired
    private MathSubmissionRepository mathSubmissionRepository;

    private Course course;

    private MathExercise exercise;

    private StudentParticipation participation;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        course = mathExerciseUtilService.addCourseWithMathExercise();
        exercise = (MathExercise) course.getExercises().iterator().next();
        participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMathSubmission_asStudent_savesSubmission() throws Exception {
        MathSubmissionDTO submissionDTO = MathExerciseFactory.generateMathSubmissionDTO(false);

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.submitted()).isFalse();
        assertThat(result.results()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMathSubmission_setsLifecycleMetadataAndFinishesParticipation() throws Exception {
        MathSubmissionDTO submissionDTO = MathExerciseFactory.generateMathSubmissionDTO(true);

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.id()).isNotNull();
        assertThat(result.submitted()).isTrue();
        assertThat(result.submissionDate()).isNotNull();

        // the server (not the client) must own the lifecycle metadata and state
        MathSubmission persisted = mathSubmissionRepository.findById(result.id()).orElseThrow();
        assertThat(persisted.getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(persisted.getSubmissionDate()).isNotNull();
        assertThat(studentParticipationTestRepository.findById(participation.getId()).orElseThrow().getInitializationState()).isEqualTo(InitializationState.FINISHED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMathSubmission_afterDueDate_returnsForbidden() throws Exception {
        // exercise whose due date has already passed, with a participation that was started before the due date.
        // Derive all dates from a single baseline so the test data is deterministic and not timing-sensitive.
        final ZonedDateTime baseTime = ZonedDateTime.now();
        MathExercise pastExercise = MathExerciseFactory.generateMathExercise(baseTime.minusDays(3), baseTime.minusDays(1), baseTime.plusDays(1), course);
        mathExerciseUtilService.saveExercise(pastExercise);
        StudentParticipation pastParticipation = participationUtilService.createAndSaveParticipationForExercise(pastExercise, TEST_PREFIX + "student1");
        pastParticipation.setInitializationDate(baseTime.minusDays(2));
        studentParticipationTestRepository.save(pastParticipation);

        MathSubmissionDTO submissionDTO = MathExerciseFactory.generateMathSubmissionDTO(true);

        request.postWithResponseBody("/api/math/exercises/" + pastExercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitMathSubmission_withValidStep_scores100() throws Exception {
        // apply add_zero_left at root: 0 + x → x (problem source=0+x, target=x)
        Long problemId = exercise.getProblems().getFirst().getId();
        var stepDTO = new MathSubmissionDTO.DerivationStepDTO(null, 0, "add_zero_left", List.of(), MathNodes.var("x"));
        var answerDTO = new MathProblemAnswerDTO(null, problemId, null, List.of(stepDTO));
        MathSubmissionDTO submissionDTO = new MathSubmissionDTO(null, true, null, null, null, List.of(answerDTO));

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.submitted()).isTrue();
        assertThat(result.answers()).hasSize(1);
        assertThat(result.answers().getFirst().steps()).hasSize(1);
        assertThat(result.results()).isNotEmpty();
        assertThat(result.results().getFirst().score()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateMathSubmission_persistsSteps() throws Exception {
        Long problemId = exercise.getProblems().getFirst().getId();
        var stepDTO = new MathSubmissionDTO.DerivationStepDTO(null, 0, "add_zero_left", List.of(), MathNodes.var("x"));
        var answerDTO = new MathProblemAnswerDTO(null, problemId, null, List.of(stepDTO));
        MathSubmissionDTO submissionDTO = new MathSubmissionDTO(null, false, null, null, null, List.of(answerDTO));

        MathSubmissionDTO result = request.putWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.answers()).hasSize(1);
        assertThat(result.answers().getFirst().steps()).hasSize(1);
        assertThat(result.answers().getFirst().steps().getFirst().appliedRuleId()).isEqualTo("add_zero_left");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitMathSubmission_withWrongStep_scores0() throws Exception {
        // wrong rule applied: result doesn't match target
        Long problemId = exercise.getProblems().getFirst().getId();
        var stepDTO = new MathSubmissionDTO.DerivationStepDTO(null, 0, "add_zero_right", List.of(), MathNodes.var("x"));
        var answerDTO = new MathProblemAnswerDTO(null, problemId, null, List.of(stepDTO));
        MathSubmissionDTO submissionDTO = new MathSubmissionDTO(null, true, null, null, null, List.of(answerDTO));

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.submitted()).isTrue();
        assertThat(result.results()).isNotEmpty();
        assertThat(result.results().getFirst().score()).isEqualTo(0.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitMathSubmission_noSteps_sourceEqualsTarget_scores100() throws Exception {
        // Edge case: problem with source == target, no steps required
        exercise.getProblems().getFirst().setSourceExpression(MathNodes.var("x"));
        exercise.getProblems().getFirst().setTargetExpression(MathNodes.var("x"));
        mathExerciseUtilService.saveExercise(exercise);

        MathSubmissionDTO submissionDTO = new MathSubmissionDTO(null, true, null, null, null, null);

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.submitted()).isTrue();
        assertThat(result.results().getFirst().score()).isEqualTo(100.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForMathEditor_withExistingSubmission_returnsSubmission() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        MathSubmissionDTO result = request.get("/api/math/participations/" + saved.getParticipation().getId() + "/math-editor", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(saved.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForMathEditor_noSubmissionYet_returnsEmptySubmission() throws Exception {
        MathSubmissionDTO result = request.get("/api/math/participations/" + participation.getId() + "/math-editor", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForMathEditor_doesNotLeakUnpublishedExampleSolutionToStudent() throws Exception {
        // The default exercise has an example solution but no publication date (unpublished), so a student must not receive it.
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        MathSubmissionDTO result = request.get("/api/math/participations/" + saved.getParticipation().getId() + "/math-editor", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result.participation()).isNotNull();
        assertThat(result.participation().exercise()).isNotNull();
        assertThat(result.participation().exercise().exampleSolution()).isNull();
        // student-facing problem instructions (description) must still be present
        assertThat(result.participation().exercise().description()).isEqualTo("Prove that 0 + x = x");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createMathSubmission_doesNotLeakUnpublishedExampleSolutionToStudent() throws Exception {
        MathSubmissionDTO submissionDTO = MathExerciseFactory.generateMathSubmissionDTO(false);

        MathSubmissionDTO result = request.postWithResponseBody("/api/math/exercises/" + exercise.getId() + "/math-submissions", submissionDTO, MathSubmissionDTO.class,
                HttpStatus.OK);

        assertThat(result.participation()).isNotNull();
        assertThat(result.participation().exercise()).isNotNull();
        assertThat(result.participation().exercise().exampleSolution()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForMathEditor_doesNotLeakExampleDerivationsToStudent() throws Exception {
        // The example derivations are the instructor's worked solution; a student must never receive them while the example solution is unpublished.
        exercise.getProblems().getFirst().setExampleDerivations(List.of(new MathSubmissionDTO.DerivationStepDTO(null, 0, "add_zero_left", List.of(), MathNodes.var("x"))));
        mathExerciseUtilService.saveExercise(exercise);
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        MathSubmissionDTO result = request.get("/api/math/participations/" + saved.getParticipation().getId() + "/math-editor", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result.participation()).isNotNull();
        assertThat(result.participation().exercise()).isNotNull();
        assertThat(result.participation().exercise().problems().getFirst().exampleDerivations()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getMathSubmissionForAssessment_asTutor_returnsSubmissionWithParticipation() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        MathSubmissionDTO result = request.get("/api/math/math-submissions/" + saved.getId() + "/for-assessment", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.participation()).isNotNull();
        assertThat(result.participation().exercise()).isNotNull();
        assertThat(result.participation().exercise().id()).isEqualTo(exercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMathSubmissionForAssessment_asStudent_returnsForbidden() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        request.get("/api/math/math-submissions/" + saved.getId() + "/for-assessment", HttpStatus.FORBIDDEN, MathSubmissionDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getMathSubmission_asOwner_returnsOk() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        MathSubmissionDTO result = request.get("/api/math/math-submissions/" + saved.getId(), HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(result.id()).isEqualTo(saved.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getMathSubmission_asOtherStudent_returnsForbidden() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", false);

        request.get("/api/math/math-submissions/" + saved.getId(), HttpStatus.FORBIDDEN, MathSubmissionDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getSubmittedMathSubmissions_asTutor_returnsList() throws Exception {
        mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        var results = request.getList("/api/math/exercises/" + exercise.getId() + "/math-submissions", HttpStatus.OK, MathSubmissionDTO.class);

        assertThat(results).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void saveManualResult_asTutor_persistsScore() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        MathSubmissionDTO result = request.putWithResponseBody("/api/math/math-submissions/" + saved.getId() + "/manual-result", new ManualResultRequestDTO(80.0),
                MathSubmissionDTO.class, HttpStatus.OK);

        assertThat(result.results()).isNotEmpty();
        assertThat(result.results().getFirst().score()).isEqualTo(80.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void saveManualResult_asStudent_returnsForbidden() throws Exception {
        MathSubmission saved = mathExerciseUtilService.createAndSaveSubmissionForExercise(exercise, TEST_PREFIX + "student1", true);

        request.put("/api/math/math-submissions/" + saved.getId() + "/manual-result", new ManualResultRequestDTO(80.0), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void suggestHints_asEnrolledStudent_returnsOk() throws Exception {
        Long problemId = exercise.getProblems().getFirst().getId();
        List<HintSuggestionDTO> hints = request.postListWithResponseBody("/api/math/exercises/" + exercise.getId() + "/problems/" + problemId + "/hints",
                new HintRequestDTO(MathNodes.var("x")), HintSuggestionDTO.class, HttpStatus.OK);

        assertThat(hints).isNotNull();
    }
}
