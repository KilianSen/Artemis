package de.tum.cit.aet.artemis.math.domain;

import static de.tum.cit.aet.artemis.exercise.domain.ExerciseType.MATH;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.SecondaryTable;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * A MathExercise.
 * <p>
 * Holds an ordered list of {@link MathProblem}s (questions), exactly like a quiz exercise holds quiz questions. A former
 * single-problem exercise is simply the {@code N = 1} case. All per-problem grading configuration lives on the
 * individual {@link MathProblem}s; the exercise keeps only the shared metadata (description, example solution).
 */
@Entity
@DiscriminatorValue(value = "R")
@SecondaryTable(name = "math_exercise_details")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MathExercise extends Exercise {

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "math_problems_order")
    private List<MathProblem> problems = new ArrayList<>();

    public List<MathProblem> getProblems() {
        return problems;
    }

    public void setProblems(List<MathProblem> problems) {
        this.problems = problems != null ? problems : new ArrayList<>();
    }

    /**
     * Adds a problem to this exercise and wires the back-reference.
     *
     * @param problem the problem to add
     */
    public void addProblem(MathProblem problem) {
        problems.add(problem);
        problem.setExercise(this);
    }

    /**
     * Re-establishes the parent back-reference on every problem so a detached exercise (e.g. deserialized from a DTO)
     * can be persisted with its problems in one cascade. Call before saving.
     */
    public void reconnectProblems() {
        if (problems == null) {
            problems = new ArrayList<>();
            return;
        }
        for (MathProblem problem : problems) {
            problem.setExercise(this);
        }
    }

    /**
     * Set all sensitive information to null, so no info with respect to the solution gets leaked to students through json
     */
    @Override
    public void filterSensitiveInformation() {
        if (!isExampleSolutionPublished()) {
            // The per-problem example derivations are the instructor's worked solution; never expose them to students before the example solution is published.
            // Only touch the collection when it is loaded — an unloaded lazy collection is not serialized either, so there is nothing to filter.
            if (Hibernate.isInitialized(problems)) {
                problems.forEach(problem -> problem.setExampleDerivations(null));
            }
        }
        super.filterSensitiveInformation();
    }

    @Override
    public String getType() {
        return "math";
    }

    @Override
    public ExerciseType getExerciseType() {
        return MATH;
    }

    @Override
    public String toString() {
        return "MathExercise{" + "id=" + getId() + "}";
    }
}
