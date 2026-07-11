import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { KatexStringPipe } from 'app/math/shared/katex-string.pipe';
import { MathNode, applyRule, distance, equalsAC, isTautology, normalizeAC } from 'app/math/shared/entities/math-node.model';
import { BlockDefinitionModel, RewriteRuleModel } from 'app/math/shared/entities/block-definition.model';
import { StepDirection } from 'app/math/shared/entities/rule-direction.model';
import { HintSuggestion } from 'app/math/shared/entities/hint-suggestion.model';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { MathNodeContext } from 'app/math/manage/update/math-math-node/math-math-node.component';
import { MathExpressionCanvasComponent } from 'app/math/shared/expression-canvas/math-expression-canvas.component';
import { MathBuilderComponent } from 'app/math/manage/update/math-builder/math-builder.component';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { ProgressBarModule } from 'primeng/progressbar';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

type StepStatus = 'pending' | 'valid' | 'invalid';

/**
 * Owns the full interactive derivation editor for a SINGLE {@link MathProblem}. Multiple instances are
 * stacked by {@link MathSubmissionComponent} so the student sees every problem in one view. All per-problem
 * configuration (goal mode, source/target/goal expressions, AC normalisation, manual mode, verification,
 * applicable-rule filtering) is read from {@link problem}; the student's collected steps are emitted upward
 * via {@link stepsChange} so the container can assemble one answer per problem.
 */
@Component({
    selector: 'jhi-math-problem-participation',
    templateUrl: './math-problem-participation.component.html',
    styleUrl: './math-problem-participation.component.scss',
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        MathNodeLatexPipe,
        KatexStringPipe,
        MathExpressionCanvasComponent,
        MathBuilderComponent,
        ButtonModule,
        CardModule,
        InputTextModule,
        MessageModule,
        ProgressBarModule,
        TagModule,
        TooltipModule,
    ],
})
export class MathProblemParticipationComponent implements OnInit {
    private mathSubmissionService = inject(MathSubmissionService);

    readonly problem = input.required<MathProblem>();
    readonly exerciseId = input.required<number>();
    readonly initialSteps = input<DerivationStep[]>([]);
    readonly submitted = input<boolean>(false);
    readonly problemIndex = input<number>(0);
    readonly scoreInPoints = input<number | undefined>(undefined);
    readonly blocks = input<BlockDefinitionModel[]>([]);

    readonly stepsChange = output<DerivationStep[]>();

    // Derivation state
    currentExpression = signal<MathNode | undefined>(undefined);
    steps = signal<DerivationStep[]>([]);

    /** Memoized rule-id → display-name lookup, recomputed only when the block registry changes (avoids a per-row scan on every change-detection cycle). */
    ruleNameById = computed<Map<string, string>>(() => {
        const map = new Map<string, string>();
        for (const block of this.blocks()) {
            for (const rule of block.rules ?? []) {
                map.set(rule.id, rule.name);
            }
        }
        return map;
    });
    selectedRuleId = signal<string>('');
    selectedDirection = signal<StepDirection>('FORWARD');
    selectedNodePath = signal<number[] | undefined>(undefined);
    ruleApplicationError = signal<string | undefined>(undefined);
    draggingPayload = signal<string | undefined>(undefined);

    selectedRule = computed<RewriteRuleModel | undefined>(() => this.allRules.find((r) => r.id === this.selectedRuleId()));

    // Manual mode state
    pendingManualStep = signal(false);
    manualResultExpression = signal<MathNode | undefined>(undefined);

    // Hint state
    hints = signal<HintSuggestion[]>([]);
    hintsLoading = signal(false);
    hintsError = signal<string | undefined>(undefined);

    // Rule palette search
    ruleSearch = signal('');

    filteredBlocks = computed<BlockDefinitionModel[]>(() => {
        const raw = this.ruleSearch().trim().toLowerCase();
        const compact = raw.replace(/\s+/g, '');

        let result = this.blocks();

        if (this.problem()?.onlyShowApplicableRules && this.selectedNodePath() !== undefined) {
            const applicable = this.applicableRuleIds();
            result = result.map((block) => ({ ...block, rules: (block.rules ?? []).filter((r) => applicable.has(r.id)) })).filter((block) => block.rules.length > 0);
        }

        if (!raw) return result;
        return result
            .map((block) => ({
                ...block,
                rules: (block.rules ?? []).filter((r) => {
                    if (r.name.toLowerCase().includes(raw)) return true;
                    const latex = (r.paletteLatex ?? '').toLowerCase().replace(/\s+/g, '');
                    return latex.includes(compact);
                }),
            }))
            .filter((block) => block.rules.length > 0);
    });

    // Verification state
    stepStatuses = signal<StepStatus[]>([]);
    stepErrors = signal<(string | undefined)[]>([]);

    submissionRootNodes = computed<MathNode[]>(() => {
        const e = this.currentExpression();
        return e ? [e] : [];
    });

    isComplete = computed(() => {
        const current = this.currentExpression();
        if (!current) return false;
        const ac = !!this.problem()?.acNormalization;
        const mode = this.problem()?.goalMode ?? 'TRANSFORMATION';
        if (mode === 'EQUATION') {
            return isTautology(canonical(current, ac)!);
        }
        const target = this.problem()?.targetExpression;
        return !!target && (equalsAC(current, target, ac) || isTautology(canonical(current, ac)!));
    });

    /** The tree to start the workspace from, based on the problem's goal mode. */
    startExpression(): MathNode | undefined {
        return (this.problem()?.goalMode ?? 'TRANSFORMATION') === 'EQUATION' ? this.problem()?.goalExpression : this.problem()?.sourceExpression;
    }

    /** Set of trees the student has already visited — start expression + every recorded step result. */
    private visitedStates = computed<Set<string>>(() => {
        const ac = !!this.problem()?.acNormalization;
        const set = new Set<string>();
        const start = this.startExpression();
        if (start) set.add(JSON.stringify(canonical(start, ac)));
        for (const step of this.steps()) set.add(JSON.stringify(canonical(step.resultExpression, ac)));
        return set;
    });

    /** Progress in [0, 1] toward the goal, or undefined when we can't compute it. */
    progress = computed<number | undefined>(() => {
        const cur = this.currentExpression();
        if (!cur) return undefined;
        const ac = !!this.problem()?.acNormalization;
        const mode = this.problem()?.goalMode ?? 'TRANSFORMATION';
        if (mode === 'EQUATION') {
            const goal = this.problem()?.goalExpression;
            if (!goal) return undefined;
            return computeProgressEquation(goal, cur, ac);
        }
        const src = this.problem()?.sourceExpression;
        const tgt = this.problem()?.targetExpression;
        if (!src || !tgt) return undefined;
        return computeProgressTransformation(src, tgt, cur, ac);
    });

    /**
     * Rule IDs that match at the currently selected node path (either direction) AND whose result is not a previously-visited
     * state under the active AC mode. Empty set when no node is selected.
     */
    applicableRuleIds = computed<Set<string>>(() => {
        const path = this.selectedNodePath();
        const current = this.currentExpression();
        if (path === undefined || !current) return new Set<string>();
        const ac = !!this.problem()?.acNormalization;
        const visited = this.visitedStates();
        const fresh = (tree: MathNode | undefined) => tree !== undefined && !visited.has(JSON.stringify(canonical(tree, ac)));
        const applicable = new Set<string>();
        for (const block of this.blocks()) {
            for (const rule of block.rules ?? []) {
                const forward = applyRule(current, path, rule.pattern, rule.template, rule.constraints ?? [], 'FORWARD', rule.direction);
                const reverse =
                    rule.direction === 'BIDIRECTIONAL' ? applyRule(current, path, rule.pattern, rule.template, rule.constraints ?? [], 'REVERSE', rule.direction) : undefined;
                if (fresh(forward) || fresh(reverse)) {
                    applicable.add(rule.id);
                }
            }
        }
        return applicable;
    });

    submissionNodeCtx = computed<MathNodeContext>(() => ({
        draggingBlockType: this.draggingPayload(),
        editable: false,
        selectFn: (_, path) => this.selectNode(path),
        dropFn: (_, path, payload) => {
            const rule = this.allRules.find((r) => r.id === payload);
            if (rule) {
                this.selectedNodePath.set(path);
                this.selectedRuleId.set(payload);
                const current = this.currentExpression();
                if (current) this.selectedDirection.set(this.pickDirection(rule, current, path));
                if (this.isManualMode) {
                    this.openManualBuilder();
                } else {
                    this.applySelectedRule();
                }
            }
        },
        valueChangeFn: () => {},
        removeFn: () => {},
        isSelectedFn: (_, path) => this.isSelectedPath(path),
    }));

    ngOnInit() {
        const initial = this.initialSteps();
        if (initial.length > 0) {
            this.steps.set([...initial]);
            this.currentExpression.set(initial[initial.length - 1].resultExpression);
            this.stepStatuses.set(initial.map(() => 'pending'));
            this.stepErrors.set(initial.map(() => undefined));
        } else {
            this.currentExpression.set(this.startExpression());
        }
    }

    get hasAstExpressions(): boolean {
        if ((this.problem()?.goalMode ?? 'TRANSFORMATION') === 'EQUATION') {
            return !!this.problem()?.goalExpression;
        }
        return !!(this.problem()?.sourceExpression && this.problem()?.targetExpression);
    }

    get isManualMode(): boolean {
        return !!this.problem()?.manualDerivation;
    }

    get canVerify(): boolean {
        return this.problem()?.allowVerification !== false;
    }

    get allRules(): RewriteRuleModel[] {
        return this.blocks().flatMap((b) => b.rules ?? []);
    }

    get operatorBlocks(): BlockDefinitionModel[] {
        return this.blocks().filter((b) => (b.slots?.length ?? 0) >= 2);
    }

    get selectedRuleName(): string {
        return this.allRules.find((r) => r.id === this.selectedRuleId())?.name ?? this.selectedRuleId();
    }

    isSelectedPath(path: number[]): boolean {
        const sel = this.selectedNodePath();
        if (sel === undefined) return false;
        return sel.length === path.length && sel.every((v, i) => v === path[i]);
    }

    selectRule(ruleId: string): void {
        this.selectedRuleId.set(ruleId);
        const rule = this.allRules.find((r) => r.id === ruleId);
        const cur = this.currentExpression();
        const path = this.selectedNodePath();
        if (rule && cur && path !== undefined) {
            this.selectedDirection.set(this.pickDirection(rule, cur, path));
        } else {
            this.selectedDirection.set('FORWARD');
        }
        this.ruleApplicationError.set(undefined);
        if (this.selectedNodePath() !== undefined) {
            if (this.isManualMode) {
                this.openManualBuilder();
            } else {
                this.applySelectedRule();
            }
        }
    }

    toggleDirection(): void {
        if (this.selectedRule()?.direction !== 'BIDIRECTIONAL') return;
        this.selectedDirection.update((d) => (d === 'FORWARD' ? 'REVERSE' : 'FORWARD'));
        if (this.selectedNodePath() !== undefined && !this.isManualMode) {
            this.applySelectedRule();
        }
    }

    selectNode(path: number[]): void {
        this.selectedNodePath.set(path);
        this.ruleApplicationError.set(undefined);
        if (this.selectedRuleId()) {
            const rule = this.allRules.find((r) => r.id === this.selectedRuleId());
            const cur = this.currentExpression();
            if (rule && cur) this.selectedDirection.set(this.pickDirection(rule, cur, path));
            if (this.isManualMode) {
                this.openManualBuilder();
            } else {
                this.applySelectedRule();
            }
        }
    }

    openManualBuilder(): void {
        this.manualResultExpression.set(this.currentExpression());
        this.pendingManualStep.set(true);
    }

    confirmManualStep(): void {
        const result = this.manualResultExpression();
        if (!result) {
            this.ruleApplicationError.set('Please build the result expression first.');
            return;
        }
        if (this.visitedStates().has(JSON.stringify(canonical(result, !!this.problem()?.acNormalization)))) {
            this.ruleApplicationError.set('This step would return to a previously visited state.');
            return;
        }
        const newStep: DerivationStep = {
            stepIndex: this.steps().length,
            appliedRuleId: this.selectedRuleId(),
            targetNodePath: this.selectedNodePath() ?? [],
            resultExpression: result,
            direction: this.selectedDirection(),
        };
        this.steps.update((s) => [...s, newStep]);
        this.stepStatuses.update((s) => [...s, 'pending']);
        this.stepErrors.update((s) => [...s, undefined]);
        this.currentExpression.set(result);
        this.selectedNodePath.set(undefined);
        this.selectedRuleId.set('');
        this.pendingManualStep.set(false);
        this.manualResultExpression.set(undefined);
        this.ruleApplicationError.set(undefined);
        this.emitSteps();
    }

    cancelManualStep(): void {
        this.pendingManualStep.set(false);
        this.manualResultExpression.set(undefined);
        this.selectedNodePath.set(undefined);
        this.selectedRuleId.set('');
        this.ruleApplicationError.set(undefined);
    }

    /**
     * Picks the application direction that actually matches at `path` for `rule` against `current`, preferring FORWARD when
     * both sides match. Falls back to FORWARD when neither matches so the regular "Rule does not apply" error surfaces.
     */
    private pickDirection(rule: RewriteRuleModel, current: MathNode, path: number[]): StepDirection {
        if (applyRule(current, path, rule.pattern, rule.template, rule.constraints ?? [], 'FORWARD', rule.direction)) {
            return 'FORWARD';
        }
        if (rule.direction === 'BIDIRECTIONAL' && applyRule(current, path, rule.pattern, rule.template, rule.constraints ?? [], 'REVERSE', rule.direction)) {
            return 'REVERSE';
        }
        return 'FORWARD';
    }

    applySelectedRule() {
        this.ruleApplicationError.set(undefined);
        const ruleId = this.selectedRuleId();
        const rule = this.allRules.find((r) => r.id === ruleId);
        if (!rule) {
            this.ruleApplicationError.set('Select a rule first.');
            return;
        }
        const current = this.currentExpression();
        if (!current) {
            this.ruleApplicationError.set('No current expression.');
            return;
        }

        const path = this.selectedNodePath() ?? [];
        const direction = this.selectedDirection();
        const newTree = applyRule(current, path, rule.pattern, rule.template, rule.constraints ?? [], direction, rule.direction);
        if (!newTree) {
            this.ruleApplicationError.set('Rule does not apply at the selected node.');
            return;
        }
        if (this.visitedStates().has(JSON.stringify(canonical(newTree, !!this.problem()?.acNormalization)))) {
            this.ruleApplicationError.set('This step would return to a previously visited state.');
            return;
        }

        const newStep: DerivationStep = {
            stepIndex: this.steps().length,
            appliedRuleId: ruleId,
            targetNodePath: path,
            resultExpression: newTree,
            direction,
        };
        this.steps.update((s) => [...s, newStep]);
        this.stepStatuses.update((s) => [...s, 'pending']);
        this.stepErrors.update((s) => [...s, undefined]);
        this.currentExpression.set(newTree);
        this.selectedNodePath.set(undefined);
        this.selectedRuleId.set('');
        this.selectedDirection.set('FORWARD');
        this.emitSteps();
    }

    // ── Rule palette DnD ──────────────────────────────────────────────────────

    onRuleDragStart(event: DragEvent, ruleId: string): void {
        this.draggingPayload.set(ruleId);
        event.dataTransfer!.setData('text/plain', ruleId);
        event.dataTransfer!.effectAllowed = 'copy';
    }

    onRuleDragEnd(): void {
        this.draggingPayload.set(undefined);
    }

    // ── Block palette DnD (gap merging) ───────────────────────────────────────

    onBlockDragStart(event: DragEvent, blockType: string): void {
        this.draggingPayload.set(blockType);
        event.dataTransfer!.setData('text/plain', blockType);
        event.dataTransfer!.effectAllowed = 'copy';
    }

    onBlockDragEnd(): void {
        this.draggingPayload.set(undefined);
    }

    handleGapDrop(event: { gapIndex: number; payload: string }): void {
        const { gapIndex, payload } = event;
        const block = this.blocks().find((b) => b.type === payload);
        if (!block) return;

        const roots = this.submissionRootNodes();
        const isMerge = (block.slots?.length ?? 0) >= 2 && gapIndex > 0 && gapIndex < roots.length;

        if (isMerge) {
            const left = roots[gapIndex - 1];
            const right = roots[gapIndex];
            const sortedSlots = [...(block.slots ?? [])].sort();
            const slots: Record<string, MathNode[]> = {};
            for (const slot of block.slots ?? []) slots[slot] = [{ type: 'number', value: '?' }];
            slots[sortedSlots[0]] = [left];
            slots[sortedSlots[1]] = [right];
            this.currentExpression.set({ type: payload, slots });
        } else if (roots.length === 0) {
            this.currentExpression.set(this.buildNode(payload));
        }

        this.draggingPayload.set(undefined);
        this.emitSteps();
    }

    private buildNode(blockType: string): MathNode {
        const block = this.blocks().find((b) => b.type === blockType);
        if (!block || !block.slots?.length) {
            return { type: blockType, value: '?' };
        }
        const slots: Record<string, MathNode[]> = {};
        for (const slot of block.slots) {
            slots[slot] = [{ type: 'number', value: '?' }];
        }
        return { type: blockType, slots };
    }

    // ── Undo / Verify ─────────────────────────────────────────────────────────

    undoLastStep() {
        this.truncateToStep(this.steps().length - 1);
    }

    truncateToStep(index: number): void {
        this.steps.update((s) => s.slice(0, index));
        this.stepStatuses.update((s) => s.slice(0, index));
        this.stepErrors.update((s) => s.slice(0, index));
        const remaining = this.steps();
        const expr = remaining.length > 0 ? remaining[remaining.length - 1].resultExpression : this.startExpression();
        this.currentExpression.set(expr);
        this.selectedNodePath.set(undefined);
        this.selectedRuleId.set('');
        this.pendingManualStep.set(false);
        this.manualResultExpression.set(undefined);
        this.ruleApplicationError.set(undefined);
        this.emitSteps();
    }

    requestHints(): void {
        const exerciseId = this.exerciseId();
        const problemId = this.problem()?.id;
        const current = this.currentExpression();
        if (!exerciseId || problemId === undefined || !current) return;
        this.hintsError.set(undefined);
        this.hintsLoading.set(true);
        this.mathSubmissionService.getHints(exerciseId, problemId, current).subscribe({
            next: (suggestions) => {
                this.hints.set(suggestions);
                // Empty is a valid answer (e.g. the backend already proves the goal, or has no single-step hint):
                // surface it explicitly so the button does not appear to do nothing.
                this.hintsError.set(suggestions.length === 0 ? 'artemisApp.mathExercise.hintsNone' : undefined);
                this.hintsLoading.set(false);
            },
            error: () => {
                this.hints.set([]);
                this.hintsLoading.set(false);
                this.hintsError.set('artemisApp.mathExercise.hintsUnavailable');
            },
        });
    }

    dismissHints(): void {
        this.hints.set([]);
        this.hintsError.set(undefined);
    }

    applyHint(hint: HintSuggestion): void {
        const rule = this.allRules.find((r) => r.id === hint.ruleId);
        if (!rule) return;
        this.selectedRuleId.set(hint.ruleId);
        this.selectedNodePath.set(hint.path);
        // Direction is encoded in the rationale string; default to FORWARD unless the hint marks reverse.
        const reverse = !!hint.rationale && hint.rationale.toLowerCase().includes('reverse');
        this.selectedDirection.set(reverse ? 'REVERSE' : 'FORWARD');
        if (this.isManualMode) {
            this.openManualBuilder();
        } else {
            this.applySelectedRule();
        }
        this.dismissHints();
    }

    verifyFullMath() {
        const source = this.startExpression();
        if (!source) return;

        const statuses: StepStatus[] = [];
        const errors: (string | undefined)[] = [];
        const stepsSnapshot = this.steps();

        stepsSnapshot.forEach((step, index) => {
            const rule = this.allRules.find((r) => r.id === step.appliedRuleId);
            if (!rule) {
                statuses.push('invalid');
                errors.push('Unknown rule: ' + step.appliedRuleId);
                return;
            }
            const prev = index === 0 ? source : stepsSnapshot[index - 1].resultExpression;
            const result = applyRule(prev, step.targetNodePath, rule.pattern, rule.template, rule.constraints ?? [], step.direction ?? 'FORWARD', rule.direction);
            const isValid = !!result && equalsAC(result, step.resultExpression, !!this.problem()?.acNormalization);
            statuses.push(isValid ? 'valid' : 'invalid');
            errors.push(isValid ? undefined : 'Rule does not produce this result from the previous expression.');
        });

        this.stepStatuses.set(statuses);
        this.stepErrors.set(errors);
    }

    get allStepsValid(): boolean {
        const statuses = this.stepStatuses();
        return statuses.length > 0 && statuses.every((s) => s === 'valid');
    }

    private emitSteps(): void {
        this.stepsChange.emit(this.steps());
    }
}

/** Returns the AC-normalised form when ac is true, the input unchanged otherwise. */
function canonical(node: MathNode | undefined, ac: boolean): MathNode | undefined {
    return ac ? normalizeAC(node) : node;
}

/** Progress in [0, 1] toward the target — how much of the initial source→target distance has been closed. */
function computeProgressTransformation(source: MathNode, target: MathNode, current: MathNode, ac: boolean): number {
    const initial = distance(canonical(source, ac), canonical(target, ac));
    if (initial <= 0) return 1;
    const remaining = distance(canonical(current, ac), canonical(target, ac));
    return Math.max(0, Math.min(1, 1 - remaining / initial));
}

/** Progress in [0, 1] toward tautology — how much of the initial side-distance has been closed. */
function computeProgressEquation(goal: MathNode, current: MathNode, ac: boolean): number {
    const initialDist = sideDistance(goal, ac);
    if (initialDist <= 0) return 1;
    const remaining = sideDistance(current, ac);
    return Math.max(0, Math.min(1, 1 - remaining / initialDist));
}

function sideDistance(node: MathNode, ac: boolean): number {
    if (node.type !== 'eq' || !node.slots) return 0;
    const left = node.slots['left']?.[0];
    const right = node.slots['right']?.[0];
    if (!left || !right) return 0;
    return distance(canonical(left, ac), canonical(right, ac));
}
