package de.tum.cit.aet.artemis.math.service;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathProblem;
import de.tum.cit.aet.artemis.math.domain.MathProblemAnswer;
import de.tum.cit.aet.artemis.math.domain.MathProblemConfig;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.grader.GraderRegistry;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.grader.HintSuggestion;
import de.tum.cit.aet.artemis.math.grader.MathGrader;
import de.tum.cit.aet.artemis.math.grader.ReachabilityReport;
import de.tum.cit.aet.artemis.math.grader.RewriteChainGrader;

/**
 * Dispatch entry-point for math grading.
 * <p>
 * Selects the appropriate {@link MathGrader} based on the configured {@link MathProblemConfig#getGraderType()} and
 * forwards the call. For a whole submission it aggregates the per-problem scores weighted by each problem's points.
 */
@Conditional(MathEnabled.class)
@Lazy
@Service
public class MathGradingService {

    private final GraderRegistry graderRegistry;

    public MathGradingService(GraderRegistry graderRegistry) {
        this.graderRegistry = graderRegistry;
    }

    /**
     * Grades a whole submission against an exercise, aggregating the per-problem scores.
     * <p>
     * For every {@link MathProblem} in the exercise, the student's matching {@link MathProblemAnswer} is graded with the
     * per-problem grader (yielding a score in [0, 100]), scaled by the problem's points, and stored on the answer via
     * {@link MathProblemAnswer#setScoreInPoints(Double)}. The overall score is {@code earnedPoints / totalPoints * 100},
     * or {@code 0} when the exercise carries no points.
     *
     * @param exercise   the exercise providing the problems and their point weights
     * @param submission the student's submission carrying one answer per problem
     * @return the aggregate score in [0, 100]
     */
    public double gradeSubmission(MathExercise exercise, MathSubmission submission) {
        List<MathProblem> problems = exercise.getProblems();
        if (problems == null || problems.isEmpty()) {
            return 0.0;
        }
        double totalPoints = 0.0;
        double earnedPoints = 0.0;
        for (MathProblem problem : problems) {
            totalPoints += problem.getPoints();
            MathProblemAnswer answer = submission.answerForProblem(problem.getId());
            List<DerivationStep> steps = answer != null ? answer.getSteps() : List.of();
            double percent = gradeSubmission(problem, steps);
            double pointsForProblem = percent / 100.0 * problem.getPoints();
            if (answer != null) {
                answer.setScoreInPoints(pointsForProblem);
            }
            earnedPoints += pointsForProblem;
        }
        if (totalPoints <= 0.0) {
            return 0.0;
        }
        return earnedPoints / totalPoints * 100.0;
    }

    /**
     * Grades a single derivation, dispatching to the grader configured on the problem configuration.
     *
     * @param config the problem configuration being graded (a {@link MathProblem})
     * @param steps  the student's ordered derivation steps
     * @return score in [0, 100]
     */
    public double gradeSubmission(MathProblemConfig config, List<DerivationStep> steps) {
        GraderType type = config.getGraderType() == null ? GraderType.REWRITE_CHAIN : config.getGraderType();
        return graderRegistry.getGrader(type).grade(config, steps).score();
    }

    /**
     * Asks the problem's grader for hint suggestions at the current state.
     *
     * @param problem      the problem being worked on
     * @param currentState the student's current math state
     * @return ranked suggestions, possibly empty
     */
    public List<HintSuggestion> suggestHints(MathProblem problem, MathNode currentState) {
        GraderType type = problem.getGraderType() == null ? GraderType.REWRITE_CHAIN : problem.getGraderType();
        return graderRegistry.getGrader(type).suggestHints(problem, currentState);
    }

    /**
     * Asks the problem's grader whether the target is automatically reachable.
     *
     * @param problem the problem to analyse
     * @return reachability report, or empty if the grader does not support this check
     */
    public Optional<ReachabilityReport> verifyReachability(MathProblem problem) {
        GraderType type = problem.getGraderType() == null ? GraderType.REWRITE_CHAIN : problem.getGraderType();
        return graderRegistry.getGrader(type).verifyReachability(problem);
    }

    /**
     * Re-exposes single-step rule application for callers that still operate on the rewrite-chain
     * engine directly. Only meaningful for the {@link GraderType#REWRITE_CHAIN} grader; throws
     * {@link UnsupportedOperationException} if another grader is configured.
     *
     * @param tree the current math tree
     * @param path the index-path to the target node
     * @param rule the rule to apply
     * @return the rewritten tree, or empty if the pattern or any constraint rejects the rule
     */
    public Optional<MathNode> applyRule(MathNode tree, List<Integer> path, RewriteRule rule) {
        MathGrader grader = graderRegistry.getGrader(GraderType.REWRITE_CHAIN);
        if (grader instanceof RewriteChainGrader rcg) {
            return rcg.applyRule(tree, path, rule);
        }
        throw new UnsupportedOperationException("applyRule is only available on the rewrite-chain grader");
    }
}
