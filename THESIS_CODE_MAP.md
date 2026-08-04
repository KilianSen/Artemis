# Thesis ↔ Code Map

| Repo | Role | Milestones |
| --- | --- | --- |
| `Artemis/` | The `MATH` exercise type inside the Artemis platform (Java/Spring server + Angular client) | MS1, MS2 |
| `Regate/` | The out-of-process grading backends behind the open wire protocol | MS3 |

Path prefixes used as shorthand below:

| Shorthand | Real path |
| --- | --- |
| `«java»` | `Artemis/src/main/java/de/tum/cit/aet/artemis/math/` |
| `«web»` | `Artemis/src/main/webapp/app/math/` |
| `«test»` | `Artemis/src/test/java/de/tum/cit/aet/artemis/math/` |
| `«pw»` | `Artemis/src/test/playwright/` |
| `«egg»` | `Regate/backends/eggregate/eggregate/` |

---

## 1 Introduction

| Thesis | Code |
| --- | --- |
| §1.1 Motivation — the derivation is the graded artifact | `«java»domain/DerivationStep.java`, `«java»domain/MathSubmission.java` |
| Figure 1 — worked induction derivation `n + 0 = n` | `Regate/conformance/fixtures/19-induction-valid.json`; editor side `«web»participate/math-induction-participation/` |
| §1.2 RQ1 (Representation) | `«java»domain/`, `«web»shared/entities/` |
| §1.2 RQ2 (Assessment) | `«java»grader/`, `«java»service/MathGradingService.java` |
| §1.2 RQ3 (Reasoning) | `Regate/GRADING_PROTOCOL.md`, `Regate/backends/` |
| §1.3 Contribution: derivation-as-data (MS1) | `«java»domain/DerivationStep.java`, `«java»domain/DerivationStepListConverter.java` |
| §1.3 Contribution: dual-engine architecture (MS1) | `«java»service/BlockRegistry.java` ↔ `«web»shared/entities/math-node.model.ts` |
| §1.3 Contribution: grader-strategy seam (MS2) | `«java»grader/MathGrader.java`, `«java»grader/GraderRegistry.java` |
| §1.3 Contribution: open grading protocol (MS3) | `Regate/GRADING_PROTOCOL.md`, `«java»regate/` |

---

## 2 Background

Background is expository; these are the code realizations of each concept.

| Thesis | Code |
| --- | --- |
| §2.1 Expressions as terms, named slots | `«java»domain/MathNode.java`, `«web»shared/entities/math-node.model.ts`, `«egg»model.py` |
| §2.2 Term rewriting — match / instantiate / replace | `«java»domain/MathNodes.java`, `«web»shared/entities/math-node.model.ts` (`matchPattern`, `instantiate`, `applyRule`), `«egg»matching.py` |
| §2.2 Wildcards, bindings, side conditions, direction | `«java»domain/RuleConstraint.java`, `«java»domain/NotEqualToConstant.java`, `«java»domain/RuleDirection.java`, `«egg»conditions.py` |
| §2.3 Equational reasoning; definedness-preserving soundness | `«egg»semantics.py` (exact evaluation, partial-function domain), `«egg»audit.py` |
| §2.3 Transformation vs. equation solving | `«java»domain/GoalMode.java`, `«web»shared/entities/goal-mode.model.ts` |
| §2.4 E-graphs and equality saturation | `«egg»backend.py`, `«egg»proof_egraph.py` |
| §2.5 Assessment and hints | `«java»grader/HintSuggestion.java`, `«java»service/ReductionStrategy.java`, `«egg»hints.py` |
| §2.6 The Artemis platform | `Artemis/` (host platform); integration points in `«java»config/MathEnabled.java` |

---

## 3 Related work

No implementation. Table 1 (positioning) is argumentative only.

---

## 4 Design

### 4.1 The expression model

| Thesis | Code |
| --- | --- |
| `MathNode`: `type`, `value`, named slots (Figure 4) | `«java»domain/MathNode.java`, `«web»shared/entities/math-node.model.ts` |
| `BlockDefinition` — structure, presentation, owned rules | `«java»domain/BlockDefinition.java`, `«web»shared/entities/block-definition.model.ts` |
| Concrete blocks (Number, Variable, Add, Sub, Mul, Fraction, Negation, Equality, Pow, Succ, Apply) | `«java»domain/blocks/` — one file per block, e.g. `AddBlockDefinition.java`, `FractionBlockDefinition.java`, `ApplyBlockDefinition.java` |
| Presentation metadata (palette symbol, precedence, associativity, layout) | `«java»domain/Associativity.java`, `«java»domain/LayoutCategory.java` |
| §4.1.1 `RewriteRule(id, name, paletteLatex, pattern, template, direction, constraints)` | `«java»domain/RewriteRule.java` |
| `RuleDirection` FORWARD_ONLY / BIDIRECTIONAL; per-step `StepDirection` | `«java»domain/RuleDirection.java`, `«java»domain/StepDirection.java` |
| `RuleConstraint` / `NotEqualToConstant` (the only implementation) | `«java»domain/RuleConstraint.java`, `«java»domain/NotEqualToConstant.java` |
| §4.1.2 The block registry — normalize, index by id, reject duplicates, cache for the client | `«java»service/BlockRegistry.java`; served by `«java»web/BlockRegistryResource.java`, consumed by `«web»manage/service/math-block-registry.service.ts` |

### 4.2 Exercises and derivations

| Thesis | Code |
| --- | --- |
| `MathExercise` extends the platform exercise; problems and per-problem config | `«java»domain/MathExercise.java`, `«java»domain/MathProblem.java`, `«java»domain/MathProblemConfig.java` |
| Exercise modes TRANSFORMATION / EQUATION / INDUCTION | `«java»domain/GoalMode.java`, `«web»shared/entities/goal-mode.model.ts` |
| Induction goal, variable, declared datatype | `«java»domain/InductionDatatype.java`, `«web»shared/entities/induction-schema.ts` |
| Behavioral flags (AC comparison, hide inapplicable rules, verification control, manual-step mode, partial credit) | `«java»domain/MathProblemConfig.java` |
| A submission is an ordered list of steps, each carrying the **full resulting tree** | `«java»domain/DerivationStep.java`, `«java»domain/MathProblemAnswer.java`, `«java»domain/MathSubmission.java` |
| `DerivationRole`, `StepKind` (Type-A rule application vs. Type-B substitution) | `«java»domain/DerivationRole.java`, `«java»domain/StepKind.java` |
| Figure 5 — program-proof induction step (`fact_aux`) | `Regate/conformance/fixtures/27-apply-recursive-sum-disproof.json`, `.../28-apply-generalized-ih-fact.json` |

### 4.3 Architecture: a shared rule source, two engines

| Thesis (Figure 6) | Code |
| --- | --- |
| `BlockDefinition` beans → `BlockRegistry` (normalize + index) | `«java»domain/blocks/` → `«java»service/BlockRegistry.java` |
| `BlockRegistryResource` — rule descriptors over REST | `«java»web/BlockRegistryResource.java`, `«java»dto/BlockDefinitionDTO.java` |
| `MathExerciseResource` — author · import | `«java»web/MathExerciseResource.java`, `«java»service/MathExerciseImportService.java` |
| `MathSubmissionResource` — validate · persist · grade | `«java»web/MathSubmissionResource.java`, `«java»service/MathSubmissionService.java` |
| Client engine `math-node.model.ts` (matchPattern / instantiate / applyRule) | `«web»shared/entities/math-node.model.ts` |
| `MathNodeLatexPipe` → KaTeX | `«web»shared/math-node-latex.pipe.ts`, `«web»shared/katex-string.pipe.ts` |
| Client math editor + palette | `«web»shared/expression-canvas/math-expression-canvas.component.ts` |

### 4.4 Grading

| Thesis | Code |
| --- | --- |
| The grader abstraction (the *seam*) | `«java»grader/MathGrader.java` |
| `GradingResult`, per-step status, hints, reachability, single-step validation | `«java»grader/GradingResult.java`, `«java»grader/StepStatus.java`, `«java»grader/StepValidation.java`, `«java»grader/HintSuggestion.java`, `«java»grader/ReachabilityReport.java` |
| Grader registry and dispatcher; first conclusive verdict; abstention outranks later failure | `«java»grader/GraderRegistry.java`, `«java»service/MathGradingService.java` |
| Grader strength ordering (structural → semantic → formal) | `«java»grader/GraderStrength.java`, `«java»grader/GraderType.java`, `«java»grader/GradingSpeed.java` |
| **Algorithm 1** — grading by trusted server-side replay | `«java»grader/PathCheckerGrader.java` |
| Partial credit from endpoint proximity; the distance metric | `«java»service/MathNodeDistance.java` |
| Hint generation (one-step look-ahead over the same metric) | `«java»grader/PathCheckerGrader.java` (`suggestHints`), `«java»service/ReductionStrategy.java` |
| Reachability check for instructors | `«java»service/ReductionStrategy.java`, `«java»dto/ReachabilityReportDTO.java` |
| Client-as-adversary posture (Appendix G) | `«java»service/MathSubmissionService.java`, `«java»domain/MathNodes.java` (`assertWildcardFree`) |

### 4.5 The grading backends

| Thesis | Code |
| --- | --- |
| §4.5.1 The wire protocol (`GradeRequest` → `GradeResponse`, CLI + HTTP) | **Spec:** `Regate/GRADING_PROTOCOL.md`. **Backend side:** `«egg»service.py`, `«egg»server.py`. **Artemis side:** `«java»regate/dto/GradeRequest.java`, `«java»regate/dto/GradeResponse.java` |
| Ruleset travels in the request as data | `«java»regate/RuleMapper.java`, `«java»regate/dto/RuleSpec.java`, `«java»regate/RegateRequestMapper.java` |
| `definitions` for `apply` operators (recursive / non-recursive) | `«java»regate/RegateVocabulary.java`, `«java»domain/blocks/ApplyBlockDefinition.java` |
| §4.5.2 Five outcomes; `score: null` is honest inconclusiveness (Figure 8) | `«java»regate/dto/Outcome.java`, `«java»regate/RegateResponseMapper.java` |
| Staged grading: sync fast grader + async certifier (Figure 9) | `«java»service/MathGradingDispatcher.java`, `«java»domain/MathGradingJob.java`, `«java»domain/MathGradingPhase.java`, `«java»domain/MathGradingJobStatus.java`, `«java»config/MathAsyncConfiguration.java`, `«java»service/MathGradingRecoveryService.java` |
| §4.5.3 **eggregate** — bounded equality saturation over trusted rules | `Regate/backends/eggregate/`; Artemis adapter `«java»regate/EggregateGrader.java` |
| ↳ step-local validator (Type-A / Type-B, valid / invalid / open) | `«egg»validate.py` |
| ↳ the e-graph as a pass-sound oracle; bounded search | `«egg»backend.py` |
| ↳ proof-producing congruence-closure e-graph (≈370 lines) | `«egg»proof_egraph.py` |
| ↳ disprover (candidate pool, definedness-aware) and prover | `«egg»semantics.py`, `«egg»robust.py` |
| ↳ `verify_rules` fuzz audit | `«egg»audit.py`, `Regate/backends/eggregate/check_rules.py` |
| ↳ per-exercise precomputation and reference-solution landmarks | `«egg»precompute.py`, `«egg»reference.py` |
| ↳ two engines over one rule representation (egglog compile + directed stepper) | `«egg»rule.py`, `«egg»hints.py`, `«egg»compare.py` |
| ↳ built-in sample ruleset | `«egg»catalogue.py` |
| §4.5.4 **cvc5regate** — SMT solver, equational + induction | `Regate/backends/cvc5regate/`: `cvc5_prover.py`, `cvc5_equiv.py`, `cvc5_induction.py`, `step_check.py`, `grade.py`; adapter `«java»regate/Cvc5regateGrader.java` |
| §4.5.4 **leanregate** — Lean/Mathlib kernel-checked certificates | `Regate/backends/leanregate/`: `lean_prover.py`, `lean_check.py`, `lean_induction.py`, `Regate/Check.lean` (the Lean package dir is also named `Regate`), `lakefile.toml`, `prune_lake.py`; adapter `«java»regate/LeanregateGrader.java` |
| §4.5.4 **coqregate** — Coq, induction only | `Regate/backends/coqregate/`: `coq_prover.py`, `coq_induction.py`, `step_check.py`; adapter `«java»regate/CoqregateGrader.java` |
| §4.5.5 Assumptions, hypotheses, derived lemmas | `«java»regate/dto/AssumptionSpec.java`, `«java»regate/dto/ConditionSpec.java`, `«java»regate/dto/StepSpec.java`; `«egg»conditions.py`, `«egg»validate.py` |
| §4.5.6 Induction: grade each case, defer or certify the schema | `«egg»backend.py` (defers); `.../cvc5regate/cvc5_induction.py`, `.../leanregate/lean_induction.py`, `.../coqregate/coq_induction.py` (certify) |
| Shared client / retry / error handling for all four | `«java»regate/AbstractRegateGrader.java`, `«java»regate/RegateClient.java`, `«java»regate/RegateException.java` |

---

## 5 Implementation

### 5.1 The model and how it is stored

| Thesis | Code |
| --- | --- |
| Feature toggle; module creates its tables once | `«java»config/MathEnabled.java` |
| Structural (value-based) equality on `MathNode` | `«java»domain/MathNode.java` |
| Tree-valued fields persisted as JSON in one text column | `«java»domain/MathNodeConverter.java`, `«java»domain/DerivationStepListConverter.java`, `«java»domain/IntegerListConverter.java` |
| Repositories | `«java»repository/MathExerciseRepository.java`, `«java»repository/MathSubmissionRepository.java`, `«java»repository/MathGradingJobRepository.java` |

### 5.2 What the server refuses

| Thesis | Code |
| --- | --- |
| Drop client-supplied step ids; reject wildcards; normalize the tree | `«java»service/MathSubmissionService.java`, `«java»domain/MathNodes.java` (`assertWildcardFree`, `normalize`) |
| Re-check ownership / course membership; block cross-exercise re-parenting | `«java»web/MathSubmissionResource.java` |
| Clear any client-injected result | `«java»service/MathSubmissionService.java` |

### 5.3 The rewrite kernel, implemented twice

| Thesis | Code |
| --- | --- |
| Server kernel | `«java»domain/MathNodes.java` |
| Client kernel — **Algorithm 2**, `getAllPossibleRewrites`, `verifyTransformation` | `«web»shared/entities/math-node.model.ts` |
| Completion predicates `isTautology`, `normalize`, `normalizeAC` | `«java»domain/MathNodes.java` and `«web»shared/entities/math-node.model.ts` (mirrored pair) |
| Path encoding: integer indices into children flattened in sorted-slot order | both kernels above; backend counterpart `.../step_check.py` in each Regate backend |

### 5.4 The student-facing client

| Thesis | Code |
| --- | --- |
| `MathNode` → LaTeX → KaTeX, precedence-aware parenthesization | `«web»shared/math-node-latex.pipe.ts`, `«web»shared/katex-string.pipe.ts` |
| **Figure 10** — the three-part derivation workspace | `«web»participate/math-submission/math-submission.component.*`, `«web»participate/math-problem-participation/` |
| Interactive canvas (click a node / drag a rule onto it) | `«web»shared/expression-canvas/math-expression-canvas.component.ts` |
| **Figure 11a** — "Suggest hint" control | `«web»participate/math-problem-participation/math-problem-participation.component.*` |
| **Figure 11b** — manual-step mode | same component + `«web»shared/expression-canvas/` |
| **Figure 12** — INDUCTION exercise expands into per-case sub-derivations | `«web»participate/math-induction-participation/math-induction-participation.component.*` |
| Client-side submission service against the three REST endpoints | `«web»participate/service/math-submission.service.ts`, `«web»manage/service/math-exercise.service.ts`, `«web»manage/service/math-block-registry.service.ts` |
| Routing | `«web»math.route.ts`, `«web»participate/math-editor.route.ts` |

### 5.5 The grading layer in Artemis

| Thesis | Code |
| --- | --- |
| The in-process grader (replay of Algorithm 1) | `«java»grader/PathCheckerGrader.java` |
| The distance metric (multiset symmetric difference of subtrees) | `«java»service/MathNodeDistance.java` |
| Dispatch by the ordering rules of §4.4 | `«java»service/MathGradingService.java`, `«java»grader/GraderRegistry.java` |
| Endpoints: hint request, tutor score override, reachability, re-evaluation | `«java»web/MathSubmissionResource.java`, `«java»web/MathExerciseResource.java`; DTOs `«java»dto/HintRequestDTO.java`, `«java»dto/HintSuggestionDTO.java`, `«java»dto/ManualResultRequestDTO.java`, `«java»dto/MathAssessmentUpdateDTO.java`, `«java»dto/ReachabilityReportDTO.java`, `«java»dto/MathGradingStatusDTO.java` |
| **Figure 13** — per-problem grader configuration + mis-ordering warning | `«web»manage/update/math-problem-edit/math-problem-edit.component.*` |
| **Figure 14a** — student "Awaiting tutor review" | `«web»participate/math-submission/math-submission.component.html` |
| **Figure 14b** — tutor assessment view | `«web»manage/assess/math-submission-assessment.component.*`, `«java»service/MathAssessmentService.java` |

### 5.6 The grading backends

| Thesis | Code |
| --- | --- |
| Canonical protocol document | `Regate/GRADING_PROTOCOL.md`, `Regate/INTEGRATION.md` |
| eggregate as a Python package; thin service layer over the `MathNode` JSON | `Regate/backends/eggregate/pyproject.toml`, `«egg»service.py`, `«egg»server.py` |
| leanregate / coqregate reuse eggregate's CLI and HTTP entry points | `.../leanregate/grade.py`, `.../coqregate/grade.py` |
| Induction emission (`define-fun-rec`, `declare-datatype`, `--quant-ind`; Lean `induction n with`) | `.../cvc5regate/cvc5_induction.py`, `.../leanregate/lean_induction.py`, `.../leanregate/Regate/Check.lean` |
| Packaging: one container image per backend, `docker compose` on separate ports | `Regate/backends/*/Dockerfile`, `Regate/docker-compose.yml`, `Regate/Makefile` |
| Conformance suite (every fixture through every backend) | `Regate/conformance/run_conformance.py`, `Regate/conformance/fixtures/` |

---

## 6 Evaluation and discussion

### 6.1 What the tests establish

| Thesis | Code |
| --- | --- |
| Mirrored kernel tested twice on the same inputs | `«test»grader/PathCheckerGraderTest.java` (Java) ↔ `«web»shared/entities/math-node.model.spec.ts` (TypeScript) |
| Unit suites over the grading pipeline | `«test»grader/GraderRegistryTest.java`, `«test»service/MathGradingServiceMultiGraderTest.java`, `«test»MathGradingRecoveryServiceTest.java` |
| Domain-model ↔ wire-protocol mapping | `«test»regate/RegateRequestMapperTest.java`, `«test»regate/RegateProtocolSerializationTest.java`, `«test»regate/RuleMapperTest.java`, `«test»regate/RegateVocabularyTest.java`, `«test»regate/RegateGraderRetryTest.java` |
| Integration tests over the REST layer | `«test»MathExerciseIntegrationTest.java`, `«test»MathSubmissionIntegrationTest.java` |
| Four Java tests that run only against live backend containers | `«test»MathRegateAsyncGradingLiveTest.java`, `«test»regate/LiveEggregateGradingTest.java`, `«test»regate/LiveCvc5InductionGradingTest.java` |
| Angular specs (engine, canvas, participation, assessment, authoring) | every `*.spec.ts` under `«web»` |
| Three Playwright specs through the real UI | `«pw»e2e/exercise/math/MathExerciseManagement.spec.ts`, `MathExerciseParticipation.spec.ts`, `MathExerciseAssessment.spec.ts`; support `«pw»support/pageobjects/exercises/math/MathParticipationPage.ts`, fixture `«pw»fixtures/exercise/math/template.json` |
| Test data builders | `«test»util/MathExerciseFactory.java`, `«test»util/MathExerciseUtilService.java` |
| **Soundness gate** — fuzz-audit rejects non-definedness-preserving rules | `«egg»audit.py`, `Regate/backends/eggregate/check_rules.py` |
| **Conformance gate** — 35 fixtures, 80 declared checks | `Regate/conformance/run_conformance.py`, `Regate/conformance/fixtures/` |
| **Formal build / kernel check** — Lean project built against Mathlib, proofs re-run | `Regate/backends/leanregate/lakefile.toml`, `lean-toolchain`, `Regate/backends/leanregate/tests/` |
| CI wiring for all three gates | `Regate/.github/workflows/ci.yml` |
| Backend package tests (126 cases) | `Regate/backends/*/tests/` |

### 6.2 Where equality saturation stops working

| Thesis | Code |
| --- | --- |
| Table 5 / Table 13 — saturation under expanding rules | `Regate/bench/bench_v3.py`, `Regate/bench/bench_v2_results.json` |
| Saturation bound, 60,000-application budget | `«egg»backend.py` |

### 6.3 What one grade costs

| Thesis | Code |
| --- | --- |
| Tables 12, 14–19 — all cost sweeps | `Regate/bench/bench_v2.py`, `Regate/bench/bench_v3.py`, `Regate/bench/README.md`, `Regate/bench/watchdog.sh` |
| eggregate's own micro-bench harness | `Regate/backends/eggregate/bench.py` |

### 6.4 Limitations and future work

| Thesis limitation | Where it lives in code |
| --- | --- |
| eggregate's soundness is empirical, not formal | `«egg»audit.py` (fuzzing, not proof) |
| leanregate cannot grade a guarded step | `.../leanregate/lean_prover.py` (`ring`/`field_simp`, no side-condition discharge) |
| The congruence-closure e-graph is a re-implementation | `«egg»proof_egraph.py` |
| Deliberately small scope (no propositional logic, no `match`, arithmetic fragment only) | `«java»domain/blocks/` (11 blocks), `«egg»model.py` |
| Rules are code contributions, not instructor-authored | `«java»domain/blocks/` — no authoring surface in `«web»manage/update/` |
| Grading and pedagogy rest on heuristics | `«java»service/MathNodeDistance.java` |
| cvc5regate could return a wrong zero on unguarded division | `.../cvc5regate/cvc5_equiv.py` (division modeled as total) |

---

## Appendices

| Appendix | Code |
| --- | --- |
| **A** Block and rule catalog (Tables 6, 7 — 11 blocks, 21 rules) | `«java»domain/blocks/` (one file per block; each owns its rules). eggregate's own 29-rule catalog: `«egg»catalogue.py` |
| **B** The rewrite kernel (Algorithm 2) | `«web»shared/entities/math-node.model.ts`; server twin `«java»domain/MathNodes.java` |
| **C** Worked example derivation | Spec/derivation shape: `Regate/examples/appendix-b.json`, `Regate/conformance/fixtures/04-valid-derivation.json`. Persistence (Listings 1–2): `«java»domain/MathNodeConverter.java`, `«java»domain/DerivationStep.java`. Grading trace (Table 10): `«java»grader/PathCheckerGrader.java`. Distances: `«java»service/MathNodeDistance.java`. Hints (Table 11): `«java»grader/PathCheckerGrader.java` (`suggestHints`) |
| **D** The authoring path (Figures 15–17) | `«web»manage/update/math-exercise-update.component.*` (form shell), `«web»manage/update/math-problem-edit/` (per-problem config, grader list, certifier slot), `«web»manage/update/math-builder/` and `«web»manage/update/math-math-node/` (tree builder), `«web»manage/update/math-starter-templates.ts` (Figure 15 templates), `«web»manage/update/math-derivation-workspace/` and `«web»manage/update/math-induction-example-workspace/` (model solutions) |
| **E** Artemis test inventory | see §6.1 table above |
| **F** Benchmark data | `Regate/bench/` |
| **G** The client as an adversary | `«java»service/MathSubmissionService.java`, `«java»web/MathSubmissionResource.java`, `«java»domain/MathNodes.java`, `«java»grader/PathCheckerGrader.java` (server-side replay) |

---

## Reverse index

| File | Thesis sections |
| --- | --- |
| `«java»domain/MathNode.java` | §2.1, §4.1, §5.1 |
| `«java»domain/MathNodes.java` | §2.2, §5.2, §5.3, App. B |
| `«java»domain/RewriteRule.java` | §4.1.1, App. A |
| `«java»domain/BlockDefinition.java` + `domain/blocks/` | §4.1, §6.4, App. A |
| `«java»domain/DerivationStep.java` | §1.3, §4.2, App. C |
| `«java»service/BlockRegistry.java` | §4.1.2, §4.3 |
| `«java»grader/MathGrader.java` | §4.4 (the seam) |
| `«java»grader/PathCheckerGrader.java` | §4.4 (Algorithm 1), §5.5, App. C, App. G |
| `«java»service/MathNodeDistance.java` | §4.4, §5.5, §6.4, App. C |
| `«java»service/MathGradingService.java` | §4.4, §5.5 |
| `«java»service/MathGradingDispatcher.java` | §4.5.2 (Figure 9) |
| `«java»regate/` | §4.5.1, §4.5.2, §5.6 |
| `«web»shared/entities/math-node.model.ts` | §4.3, §5.3, App. B |
| `«web»shared/math-node-latex.pipe.ts` | §5.4 |
| `Regate/GRADING_PROTOCOL.md` | §4.5.1, §5.6 |
| `«egg»backend.py` | §2.4, §4.5.3, §6.2 |
| `«egg»validate.py` | §4.5.3, §4.5.5 |
| `«egg»proof_egraph.py` | §4.5.3, §5.6, §6.4 |
| `«egg»audit.py` | §4.5.3, §6.1, §6.4 |
| `Regate/conformance/` | §6.1 |
| `Regate/bench/` | §6.2, §6.3, App. F |
