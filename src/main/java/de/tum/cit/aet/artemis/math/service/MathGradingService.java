package de.tum.cit.aet.artemis.math.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import de.tum.cit.aet.artemis.math.grader.GraderStrength;
import de.tum.cit.aet.artemis.math.grader.GraderType;
import de.tum.cit.aet.artemis.math.grader.GradingResult;
import de.tum.cit.aet.artemis.math.grader.HintSuggestion;
import de.tum.cit.aet.artemis.math.grader.MathGrader;
import de.tum.cit.aet.artemis.math.grader.PathCheckerGrader;
import de.tum.cit.aet.artemis.math.grader.ReachabilityReport;
import de.tum.cit.aet.artemis.math.regate.RegateException;

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

    private static final Logger log = LoggerFactory.getLogger(MathGradingService.class);

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
     * @return the aggregate verdict: a conclusive score in [0, 100], or {@link GradingResult#inconclusive} to route to review
     */
    public GradingResult gradeSubmission(MathExercise exercise, MathSubmission submission) {
        List<MathProblem> problems = exercise.getProblems();
        if (problems == null || problems.isEmpty()) {
            return GradingResult.of(0.0);
        }
        for (MathProblem problem : problems) {
            MathProblemAnswer answer = submission.answerForProblem(problem.getId());
            if (answer == null) {
                continue;
            }
            List<DerivationStep> steps = List.copyOf(answer.getSteps());
            GradingResult result = gradeProblem(problem, steps);
            applyVerdict(answer, problem, result);
            // A configured slow certifier will re-grade this answer on the slow lane (Phase 2b); flag it pending.
            answer.setCertificationPending(problem.getCertifyingGraderType() != null);
        }
        return aggregate(exercise, submission);
    }

    /**
     * Re-grades, on the slow lane, every answer whose problem configures a
     * {@link MathProblem#getCertifyingGraderType() certifier} (Phase 2b). The certifier is the formal prover and is
     * authoritative: a conclusive verdict overwrites the fast preliminary one; an inconclusive certifier leaves the
     * preliminary verdict untouched. The pending flag is cleared regardless.
     *
     * @param exercise   the exercise providing the problems and their certifiers
     * @param submission the student's submission (answers already carry the preliminary verdict)
     * @return the re-aggregated verdict after certification
     */
    public GradingResult certifySubmission(MathExercise exercise, MathSubmission submission) {
        List<MathProblem> problems = exercise.getProblems();
        if (problems == null || problems.isEmpty()) {
            return GradingResult.of(0.0);
        }
        for (MathProblem problem : problems) {
            GraderType certifier = problem.getCertifyingGraderType();
            MathProblemAnswer answer = submission.answerForProblem(problem.getId());
            if (certifier == null || answer == null) {
                continue;
            }
            List<DerivationStep> steps = List.copyOf(answer.getSteps());
            GradingResult certified = certifier.isRemote() && steps.isEmpty() ? GradingResult.of(0.0) : gradeProblemWith(certifier, problem, steps);
            if (certified.conclusive()) {
                applyVerdict(answer, problem, certified);
            }
            answer.setCertificationPending(false);
        }
        return aggregate(exercise, submission);
    }

    /** Records a grader's verdict on the answer (outcome/certified/feedback/witness + earned points, or null points when inconclusive). */
    private void applyVerdict(MathProblemAnswer answer, MathProblem problem, GradingResult result) {
        answer.setGradingOutcome(result.outcome());
        answer.setCertified(result.certified());
        answer.setGradingFeedback(result.message());
        answer.setWitness(result.witness());
        answer.setScoreInPoints(result.conclusive() ? result.score() / 100.0 * problem.getPoints() : null);
    }

    /** Aggregates the per-answer earned points into an overall score, or an inconclusive verdict if any problem is undecided. */
    private GradingResult aggregate(MathExercise exercise, MathSubmission submission) {
        double totalPoints = 0.0;
        double earnedPoints = 0.0;
        boolean conclusive = true;
        for (MathProblem problem : exercise.getProblems()) {
            totalPoints += problem.getPoints();
            Double score = earnedPointsForProblem(problem, submission);
            if (score == null) {
                conclusive = false;
                continue;
            }
            earnedPoints += score;
        }
        if (!conclusive) {
            return GradingResult.inconclusive("At least one problem could not be graded automatically and needs review");
        }
        if (totalPoints <= 0.0) {
            return GradingResult.of(0.0);
        }
        return GradingResult.of(earnedPoints / totalPoints * 100.0);
    }

    /**
     * The earned points for a problem: the answer's stored score if present, else graded directly (an in-process source==target scores without steps; a remote grader scores 0 on
     * an empty submission).
     */
    private Double earnedPointsForProblem(MathProblem problem, MathSubmission submission) {
        MathProblemAnswer answer = submission.answerForProblem(problem.getId());
        if (answer != null) {
            return answer.getScoreInPoints();
        }
        GradingResult result = gradeProblem(problem, List.of());
        return result.conclusive() ? result.score() / 100.0 * problem.getPoints() : null;
    }

    /**
     * Grades a single derivation with the graders configured on the problem, taking the first conclusive verdict.
     * <p>
     * The problem's {@link MathProblemConfig#getGraderTypes() configured graders} that support the problem's goal mode
     * are run in configured order until one produces a <em>trustworthy</em> conclusive verdict, which is returned. An
     * inconclusive result — e.g. a remote backend outage — falls through to the next grader, so selecting several
     * same-mode backends yields redundancy.
     * <p>
     * Verdicts are arbitrated by {@link GraderStrength}: a conclusive <b>pass</b> (a full score) from any grader is
     * trusted, but a conclusive <b>fail</b> from a grader <em>weaker</em> than one that already abstained is not — the
     * answer may rely on reasoning the weaker grader cannot express, so it is not allowed to override the stronger
     * grader's abstention and the problem routes to review instead. If no grader yields a trustworthy verdict the
     * result is inconclusive (routing to review).
     *
     * @param config the problem configuration being graded (a {@link MathProblem})
     * @param steps  the student's ordered derivation steps
     * @return the grader's verdict (a conclusive score, or inconclusive)
     */
    public GradingResult gradeProblem(MathProblemConfig config, List<DerivationStep> steps) {
        GradingResult last = null;
        GraderStrength strongestAbstention = null;
        for (GraderType type : applicableGraders(config)) {
            // An unattempted problem on a remote grader scores 0: the backends reject an empty submission, so grading it
            // would route the whole (otherwise-complete) submission to review and never produce a result.
            GradingResult result = type.isRemote() && steps.isEmpty() ? GradingResult.of(0.0) : gradeProblemWith(type, config, steps);
            if (!result.conclusive()) {
                // Record that a grader of this strength could not decide, so a weaker grader's fail cannot override it.
                if (strongestAbstention == null || type.getStrength().compareTo(strongestAbstention) > 0) {
                    strongestAbstention = type.getStrength();
                }
                last = result;
                continue;
            }
            boolean fullPass = result.score() >= 100.0;
            // A pass is sound from any tier; a fail is authoritative only if no strictly stronger grader abstained.
            if (fullPass || strongestAbstention == null || type.getStrength().compareTo(strongestAbstention) >= 0) {
                return result;
            }
            // A weaker grader rejected an answer a stronger grader could not decide — distrust it and route to review.
            last = GradingResult.inconclusive("A weaker grader rejected an answer a stronger grader could not decide; routed to review");
        }
        return last != null ? last : GradingResult.inconclusive("No grader configured");
    }

    /**
     * Resolves the graders to run for a problem: the configured graders that support the problem's goal mode, in
     * configured order. Falls back to all configured graders when none supports the mode (a misconfiguration the
     * authoring layer normally prevents), and to {@link GraderType#PATH_CHECKER} when none is configured at all.
     *
     * @param config the problem configuration
     * @return the non-empty, ordered list of graders to attempt
     */
    private List<GraderType> applicableGraders(MathProblemConfig config) {
        List<GraderType> configured = config.getGraderTypes();
        if (configured == null || configured.isEmpty()) {
            return List.of(GraderType.PATH_CHECKER);
        }
        List<GraderType> supporting = configured.stream().filter(type -> type.supports(config.getGoalMode())).toList();
        return supporting.isEmpty() ? configured : supporting;
    }

    /**
     * Grades a single derivation with a specific backend — used to run the fast primary and the slow certifier lanes.
     *
     * @param type   the grader backend to use
     * @param config the problem configuration being graded
     * @param steps  the student's ordered derivation steps
     * @return the grader's verdict (a conclusive score, or inconclusive on a backend failure)
     */
    public GradingResult gradeProblemWith(GraderType type, MathProblemConfig config, List<DerivationStep> steps) {
        try {
            return graderRegistry.getGrader(type).grade(config, steps);
        }
        catch (RegateException e) {
            // A remote backend failure/timeout must never fail the submit; route the answer to manual review.
            log.warn("Remote grader {} failed; routing submission to review: {}", type, e.getMessage());
            return GradingResult.inconclusive("Automatic grading is temporarily unavailable; routed to review");
        }
    }

    /**
     * Asks the problem's grader for hint suggestions at the current state.
     *
     * @param problem      the problem being worked on
     * @param currentState the student's current math state
     * @return ranked suggestions, possibly empty
     */
    public List<HintSuggestion> suggestHints(MathProblem problem, MathNode currentState) {
        return graderRegistry.getGrader(applicableGraders(problem).getFirst()).suggestHints(problem, currentState);
    }

    /**
     * Asks the problem's grader whether the target is automatically reachable.
     *
     * @param problem the problem to analyse
     * @return reachability report, or empty if the grader does not support this check
     */
    public Optional<ReachabilityReport> verifyReachability(MathProblem problem) {
        return graderRegistry.getGrader(applicableGraders(problem).getFirst()).verifyReachability(problem);
    }

    /**
     * Re-exposes single-step rule application for callers that still operate on the path checker
     * engine directly. Only meaningful for the {@link GraderType#PATH_CHECKER} grader; throws
     * {@link UnsupportedOperationException} if another grader is configured.
     *
     * @param tree the current math tree
     * @param path the index-path to the target node
     * @param rule the rule to apply
     * @return the rewritten tree, or empty if the pattern or any constraint rejects the rule
     */
    public Optional<MathNode> applyRule(MathNode tree, List<Integer> path, RewriteRule rule) {
        MathGrader grader = graderRegistry.getGrader(GraderType.PATH_CHECKER);
        if (grader instanceof PathCheckerGrader rcg) {
            return rcg.applyRule(tree, path, rule);
        }
        throw new UnsupportedOperationException("applyRule is only available on the path checker grader");
    }
}
