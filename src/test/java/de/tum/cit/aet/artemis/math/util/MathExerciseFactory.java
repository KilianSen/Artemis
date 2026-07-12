package de.tum.cit.aet.artemis.math.util;

import java.time.ZonedDateTime;
import java.util.List;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.util.ExerciseFactory;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.dto.MathExerciseDTO;
import de.tum.cit.aet.artemis.math.dto.MathProblemDTO;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO;

public class MathExerciseFactory {

    /** Source expression: {@code 0 + x} */
    public static MathNode sampleSource() {
        return MathNodes.add(MathNodes.num("0"), MathNodes.var("x"));
    }

    /** Target expression: {@code x} */
    public static MathNode sampleTarget() {
        return MathNodes.var("x");
    }

    /** A single sample problem transforming {@code 0 + x} into {@code x}, worth 10 points. */
    public static MathProblem sampleProblem() {
        MathProblem problem = new MathProblem();
        problem.setTitle("Problem 1");
        problem.setPoints(10.0);
        problem.setSourceExpression(sampleSource());
        problem.setTargetExpression(sampleTarget());
        return problem;
    }

    public static MathExercise generateMathExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        var exercise = (MathExercise) ExerciseFactory.populateExercise(new MathExercise(), releaseDate, dueDate, assessmentDueDate, course);
        exercise.setProblemStatement("Prove that 0 + x = x");
        exercise.addProblem(sampleProblem());
        exercise.setMaxPoints(10.0);
        return exercise;
    }

    public static MathSubmission generateMathSubmission(boolean submitted) {
        var submission = new MathSubmission();
        submission.setSubmitted(submitted);
        submission.setSubmissionDate(ZonedDateTime.now());
        return submission;
    }

    /** A problem DTO carrying the sample {@code 0 + x -> x} configuration, worth 10 points. */
    public static MathProblemDTO sampleProblemDTO() {
        return new MathProblemDTO(null, "Problem 1", 10.0, sampleSource(), sampleTarget(), null, null, null, null, false, false, false, true, false, null, null);
    }

    public static MathExerciseDTO generateMathExerciseDTO(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Course course) {
        return new MathExerciseDTO(null, "Math Exercise", null, "Prove that 0 + x = x.", null, null, 10.0, 0.0, IncludedInOverallScore.INCLUDED_COMPLETELY, false, false, false,
                false, null, null, releaseDate, null, dueDate, assessmentDueDate, null, course.getId(), List.of(sampleProblemDTO()), null);
    }

    public static MathSubmissionDTO generateMathSubmissionDTO(boolean submitted) {
        return new MathSubmissionDTO(null, submitted, null, null, null, null, null);
    }

}
