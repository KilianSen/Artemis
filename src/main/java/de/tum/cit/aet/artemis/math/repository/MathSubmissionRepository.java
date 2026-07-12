package de.tum.cit.aet.artemis.math.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;

/**
 * Spring Data JPA repository for the MathSubmission entity.
 */
@Conditional(MathEnabled.class)
@Lazy
@Repository
public interface MathSubmissionRepository extends JpaRepository<MathSubmission, Long> {

    @Query("SELECT s FROM MathSubmission s LEFT JOIN FETCH s.answers a LEFT JOIN FETCH a.steps LEFT JOIN FETCH s.results WHERE s.id = :id")
    Optional<MathSubmission> findByIdWithAnswersAndResults(@Param("id") Long id);

    @Query("""
            SELECT s FROM MathSubmission s
                LEFT JOIN FETCH s.answers a
                LEFT JOIN FETCH a.steps
                LEFT JOIN FETCH s.results
                LEFT JOIN FETCH s.participation p
                LEFT JOIN FETCH p.exercise
            WHERE s.id = :id
            """)
    Optional<MathSubmission> findByIdWithAnswersResultsAndParticipation(@Param("id") Long id);

    @Query("""
            SELECT s FROM MathSubmission s
                LEFT JOIN FETCH s.results result
                LEFT JOIN FETCH result.feedbacks
                LEFT JOIN FETCH result.assessor
            WHERE s.id = :id
            """)
    Optional<MathSubmission> findByIdWithResultsAndFeedbacksAndAssessor(@Param("id") Long id);

    @Query("""
            SELECT s FROM MathSubmission s
            LEFT JOIN FETCH s.results
            LEFT JOIN FETCH s.answers a
            LEFT JOIN FETCH a.steps
            LEFT JOIN FETCH s.participation p
            LEFT JOIN FETCH p.student
            WHERE p.exercise.id = :exerciseId AND s.submitted = true
            ORDER BY s.submissionDate DESC
            """)
    java.util.List<MathSubmission> findSubmittedByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Submitted submissions of an exercise that carry no result at all — i.e. automatic grading was inconclusive/failed
     * (escalated to review) and no tutor has picked them up yet (a locked submission carries an empty result, so it is
     * excluded). These are the submissions eligible for a new manual assessment. Auto-graded submissions have a result and
     * are therefore not offered.
     *
     * @param exerciseId the exercise whose assessable submissions to list
     * @return the oldest-first list of submitted, unassessed, unlocked submissions
     */
    @Query("""
            SELECT s FROM MathSubmission s
                LEFT JOIN FETCH s.answers a
                LEFT JOIN FETCH a.steps
                LEFT JOIN FETCH s.participation p
                LEFT JOIN FETCH p.student
            WHERE p.exercise.id = :exerciseId AND s.submitted = true AND s.results IS EMPTY
            ORDER BY s.submissionDate ASC
            """)
    java.util.List<MathSubmission> findAssessableByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Submitted submissions of an exercise that the given tutor has started or completed a manual assessment for (a
     * {@code MANUAL} result whose assessor is the tutor). Drives the "assessed by me" list in the assessment dashboard.
     *
     * @param exerciseId the exercise
     * @param assessorId the tutor whose assessments to list
     * @return the submissions this tutor has a manual result on
     */
    @Query("""
            SELECT DISTINCT s FROM MathSubmission s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH s.answers a
                LEFT JOIN FETCH a.steps
                LEFT JOIN FETCH s.participation p
                LEFT JOIN FETCH p.student
            WHERE p.exercise.id = :exerciseId AND s.submitted = true
                AND EXISTS (
                    SELECT r2 FROM s.results r2
                    WHERE r2.assessor.id = :assessorId AND r2.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL
                )
            """)
    java.util.List<MathSubmission> findAssessedByTutor(@Param("exerciseId") Long exerciseId, @Param("assessorId") Long assessorId);
}
