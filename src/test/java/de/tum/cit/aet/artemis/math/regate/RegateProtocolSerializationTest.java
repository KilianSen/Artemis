package de.tum.cit.aet.artemis.math.regate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.regate.dto.GradeRequest;
import de.tum.cit.aet.artemis.math.regate.dto.GradeResponse;
import de.tum.cit.aet.artemis.math.regate.dto.StepSpec;

/**
 * Locks the exact Regate wire shape produced/consumed by the protocol DTOs: snake_case option keys, the
 * reserved-word field renames ({@code final}, {@code var}), lowercase {@code outcome}, null {@code score},
 * empty-path transmission, and tolerance of unknown response fields.
 */
class RegateProtocolSerializationTest {

    private final ObjectMapper mapper = JsonObjectMapper.get();

    @Test
    void serializesRequestWithProtocolFieldNamesAndOmitsNulls() throws Exception {
        var options = new GradeRequest.OptionsSpec(true, 5, false, null, null, null, null);
        var exercise = new GradeRequest.ExerciseSpec("ex1", "transformation", MathNodes.var("x"), MathNodes.var("y"), null, null, List.of(), null, null, null, null, options);
        // a root-targeting step: empty path must survive serialization (NON_NULL, not NON_EMPTY)
        var step = new StepSpec("add_comm", List.of(), "forward", "A", null, MathNodes.var("y"));
        var submission = new GradeRequest.SubmissionSpec(null, List.of(step), null, null, null);
        var request = new GradeRequest(GradeRequest.PROTOCOL_VERSION, exercise, submission);

        JsonNode json = mapper.valueToTree(request);

        assertThat(json.get("protocol").asText()).isEqualTo("1.0");
        // snake_case option keys
        assertThat(json.at("/exercise/options/partial_credit").asBoolean()).isTrue();
        assertThat(json.at("/exercise/options").has("audit_rules")).isFalse(); // null omitted
        assertThat(json.at("/exercise/options").has("verify_rules")).isFalse(); // null omitted: Artemis trusts its code-contributed ruleset
        // reserved-word renames and empty path present
        var stepJson = json.at("/submission/steps/0");
        assertThat(stepJson.get("kind").asText()).isEqualTo("A");
        assertThat(stepJson.get("path").isArray()).isTrue();
        assertThat(stepJson.get("path")).isEmpty();
        assertThat(stepJson.has("equation")).isFalse(); // null omitted
        // 'final' is absent when null; 'inductionVar' is camelCase per the protocol
        assertThat(json.at("/submission").has("final")).isFalse();
        assertThat(json.at("/exercise").has("inductionVar")).isFalse();
    }

    @Test
    void serializesVerifyRulesWhenSet() throws Exception {
        var options = new GradeRequest.OptionsSpec(null, null, null, null, null, null, true);
        var exercise = new GradeRequest.ExerciseSpec("ex1", "transformation", MathNodes.var("x"), MathNodes.var("y"), null, null, List.of(), null, null, null, null, options);
        var request = new GradeRequest(GradeRequest.PROTOCOL_VERSION, exercise, new GradeRequest.SubmissionSpec(MathNodes.var("y"), null, null, null, null));

        JsonNode json = mapper.valueToTree(request);

        assertThat(json.at("/exercise/options/verify_rules").asBoolean()).isTrue();
    }

    @Test
    void deserializesResponseWithLowercaseOutcomeNullScoreAndUnknownFields() throws Exception {
        String body = """
                {
                  "protocol": "1.0",
                  "backend": "leanregate",
                  "backend_version": "0.3.1",
                  "outcome": "equal_no_certificate",
                  "score": null,
                  "certified": false,
                  "feedback": "believed equal; no kernel certificate",
                  "steps": [ { "index": 0, "status": "valid", "reason": "" } ],
                  "meta": { "ms": 42, "induction": { "schema": "assumed" } },
                  "some_future_field": 123
                }
                """;

        GradeResponse response = mapper.readValue(body, GradeResponse.class);

        assertThat(response.backend()).isEqualTo("leanregate");
        assertThat(response.backendVersion()).isEqualTo("0.3.1");
        assertThat(response.outcome()).isEqualTo(de.tum.cit.aet.artemis.math.regate.dto.Outcome.EQUAL_NO_CERTIFICATE);
        assertThat(response.score()).isNull();
        assertThat(response.certified()).isFalse();
        assertThat(response.steps()).singleElement().satisfies(s -> assertThat(s.status()).isEqualTo("valid"));
        assertThat(response.meta().ms()).isEqualTo(42); // unknown nested `induction` key ignored
    }

    @Test
    void deserializesPartialCreditScoreAndBackendSpecificProofCertificate() throws Exception {
        // The hardened contract allows a conclusive score anywhere in 0..100, and lets a formal backend attach a
        // single-element proof whose shape is an engine-artifact object ({engine,method,smtlib,expect}) rather than
        // eggregate's {rule,path,direction,state}. Artemis does not consume `proof`, so its unknown element fields
        // must be ignored (ProofStep is @JsonIgnoreProperties(ignoreUnknown = true)) — never a deserialization break.
        String body = """
                {
                  "protocol": "1.0",
                  "backend": "cvc5regate",
                  "backend_version": "0.1.0",
                  "outcome": "proven_equal",
                  "score": 73,
                  "certified": true,
                  "proof": [ { "engine": "cvc5", "method": "quant-ind", "smtlib": "(set-logic ...)", "expect": "unsat" } ],
                  "feedback": "provably equivalent",
                  "meta": { "ms": 12, "rechecked": false }
                }
                """;

        GradeResponse response = mapper.readValue(body, GradeResponse.class);

        assertThat(response.score()).isEqualTo(73); // partial credit passes through
        assertThat(response.certified()).isTrue();
        assertThat(response.proof()).singleElement().satisfies(step -> {
            // Engine-artifact fields are unknown to ProofStep and dropped; nothing throws.
            assertThat(step.rule()).isNull();
            assertThat(step.state()).isNull();
        });
    }
}
