package de.tum.cit.aet.artemis.math.regate.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.math.domain.MathNode;

/**
 * The Regate {@code GradeRequest} wire object (see {@code GRADING_PROTOCOL.md}). MathNodes are in the
 * protocol vocabulary, which Artemis now uses natively — no translation.
 * <p>
 * The three modes ({@code transformation} / {@code equation} / {@code induction}) share one
 * {@link ExerciseSpec}; the fields not relevant to a mode are left {@code null} and omitted on the wire.
 *
 * @param protocol   protocol version, always {@code "1.0"}
 * @param exercise   the exercise/problem specification
 * @param submission the student's submission
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GradeRequest(String protocol, ExerciseSpec exercise, SubmissionSpec submission) {

    public static final String PROTOCOL_VERSION = "1.0";

    /**
     * The exercise specification. For {@code transformation}/{@code equation} the goal is carried by
     * {@code source}/{@code target}; for {@code induction} by {@code goal}/{@code inductionVar}/{@code definitions}.
     * The ruleset always travels inline in {@code ruleset} (leanregate has no built-in catalogue).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExerciseSpec(String id, String mode, MathNode source, MathNode target, MathNode goal, String inductionVar, List<RuleSpec> ruleset, List<MathNode> reference,
            List<AssumptionSpec> assumptions, List<MathNode> hypotheses, List<RuleSpec> definitions, OptionsSpec options, String domain, DatatypeSpec datatype) {
    }

    /**
     * The inductive datatype the induction variable ranges over (see {@code GRADING_PROTOCOL.md} "Datatype
     * induction", since 1.1). Absent ⇒ ℕ (legacy {@code 0}/{@code succ}). Exactly one non-recursive ("base") and one
     * recursive ("step") constructor are supported; a field whose {@code sort} equals the datatype {@code name} is a
     * recursive position and yields an induction hypothesis.
     *
     * @param name         the datatype name (e.g. {@code "Lst"}, {@code "Tree"})
     * @param constructors the constructors (one base, one recursive)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DatatypeSpec(String name, List<ConstructorSpec> constructors) {

        /**
         * @param name   the constructor name (e.g. {@code "nil"}, {@code "cons"})
         * @param fields the constructor's fields in order
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ConstructorSpec(String name, List<FieldSpec> fields) {
        }

        /**
         * @param name the field name
         * @param sort {@code "int"} / {@code "rat"} for a numeric field, or the datatype's own name for a recursive position
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record FieldSpec(String name, String sort) {
        }
    }

    /**
     * Grading options. Wire names are snake_case; a null field is omitted so the backend applies its documented
     * default. {@code verifyRules} discharges the ruleset trust boundary at request time (re-establish soundness
     * instead of taking the caller's warrant) — Artemis leaves it {@code null} (trust), because its catalogue is an
     * upstream, code-reviewed contribution, not runtime-authored data.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OptionsSpec(@JsonProperty("partial_credit") Boolean partialCredit, Integer bound, @JsonProperty("want_hint") Boolean wantHint,
            @JsonProperty("audit_rules") Boolean auditRules, @JsonProperty("audit_trials") Integer auditTrials, @JsonProperty("ac_normalization") Boolean acNormalization,
            @JsonProperty("verify_rules") Boolean verifyRules) {
    }

    /**
     * The student's submission. {@code steps} (per-step grading) and/or {@code finalExpression} (endpoint
     * equivalence) are used for transformation/equation; {@code base}/{@code step} for induction.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubmissionSpec(@JsonProperty("final") MathNode finalExpression, List<StepSpec> steps, List<LemmaSpec> lemmas, Derivation base, Derivation step) {
    }

    /** An auxiliary lemma proven on the way: a self-contained sub-derivation from its own {@code source}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LemmaSpec(MathNode source, List<StepSpec> steps) {
    }

    /** A derivation obligation (used for the induction {@code base} and {@code step} proofs). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Derivation(List<StepSpec> steps) {
    }
}
