import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe, NgClass } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { MathProblemAnswer } from 'app/math/shared/entities/math-problem-answer.model';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { MathNode, isTautology, mathNodesEqual } from 'app/math/shared/entities/math-node.model';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';

@Component({
    selector: 'jhi-math-submission-assessment',
    templateUrl: './math-submission-assessment.component.html',
    styleUrl: './math-submission-assessment.component.scss',
    imports: [
        AssessmentLayoutComponent,
        UnreferencedFeedbackComponent,
        TranslateDirective,
        HtmlForMarkdownPipe,
        MathNodeLatexPipe,
        NgClass,
        DecimalPipe,
        ArtemisDatePipe,
        FormsModule,
        ButtonModule,
        CardModule,
        InputGroupModule,
        InputGroupAddonModule,
        InputTextModule,
        TagModule,
    ],
})
export class MathSubmissionAssessmentComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private blockRegistryService = inject(MathBlockRegistryService);
    private mathSubmissionService = inject(MathSubmissionService);

    readonly mathExercise = signal<MathExercise>(undefined!);
    readonly submission = signal<MathSubmission>(undefined!);
    readonly result = signal<Result | undefined>(undefined);
    readonly courseId = signal<number>(-1);

    readonly isLoading = signal<boolean>(true);
    readonly saveBusy = signal<boolean>(false);
    manualScore: number | undefined;
    /** The tutor's free-text (unreferenced) feedback comments, round-tripped with the manual result. */
    readonly unreferencedFeedback = signal<Feedback[]>([]);
    readonly saveSuccess = signal<boolean>(false);
    blocks = signal<BlockDefinitionModel[]>([]);

    /** Memoized rule-id → display-name lookup, recomputed only when the block registry changes (avoids a per-row nested scan on every change-detection cycle). */
    ruleNameById = computed<Map<string, string>>(() => {
        const map = new Map<string, string>();
        for (const block of this.blocks()) {
            for (const rule of block.rules ?? []) {
                map.set(rule.id, rule.name);
            }
        }
        return map;
    });

    /** The ordered problems of the exercise under review. */
    readonly problems = computed<MathProblem[]>(() => this.mathExercise()?.problems ?? []);

    /** Aggregate points earned across all answered problems. */
    readonly earnedPoints = computed<number>(() => (this.submission()?.answers ?? []).reduce((sum, answer) => sum + (answer.scoreInPoints ?? 0), 0));

    /** Sum of the exercise's problem points. */
    readonly maxPoints = computed<number>(() => this.problems().reduce((sum, problem) => sum + (problem.points ?? 0), 0));

    ngOnInit() {
        this.route.data.subscribe(({ mathSubmission }) => {
            if (mathSubmission) {
                this.submission.set(mathSubmission as MathSubmission);
                this.mathExercise.set(this.submission().participation?.exercise as MathExercise);
                this.result.set(this.submission().results?.[this.submission().results!.length - 1]);
                this.unreferencedFeedback.set(this.result()?.feedbacks ?? []);
                this.isLoading.set(false);
            }
        });

        // Walk up the route tree to find courseId
        let snapshot = this.route.snapshot;
        while (snapshot) {
            if (snapshot.params['courseId']) {
                this.courseId.set(Number(snapshot.params['courseId']));
                break;
            }
            snapshot = snapshot.parent!;
        }

        this.blockRegistryService.getBlockRegistry().subscribe({
            next: (blocks) => this.blocks.set(blocks),
        });
    }

    saveManualScore(): void {
        if (this.manualScore == undefined || this.manualScore < 0 || this.manualScore > 100) return;
        this.saveBusy.set(true);
        this.saveSuccess.set(false);
        this.mathSubmissionService.saveManualResult(this.submission().id!, this.manualScore, this.unreferencedFeedback()).subscribe({
            next: (updated) => {
                this.result.set(updated.results?.[updated.results.length - 1]);
                this.unreferencedFeedback.set(this.result()?.feedbacks ?? []);
                this.saveBusy.set(false);
                this.saveSuccess.set(true);
            },
            error: () => {
                this.saveBusy.set(false);
            },
        });
    }

    /** The student's answer for a given problem, if any. */
    answerForProblem(problem: MathProblem): MathProblemAnswer | undefined {
        return (this.submission()?.answers ?? []).find((answer) => answer.problemId === problem.id);
    }

    /** The expression a problem's derivation starts from. */
    startExpression(problem: MathProblem): MathNode | undefined {
        return (problem.goalMode ?? 'TRANSFORMATION') === 'EQUATION' ? problem.goalExpression : problem.sourceExpression;
    }

    hasExpressions(problem: MathProblem): boolean {
        if ((problem.goalMode ?? 'TRANSFORMATION') === 'EQUATION') {
            return !!problem.goalExpression;
        }
        return !!(problem.sourceExpression && problem.targetExpression);
    }

    /** Whether a list of steps reaches the problem's goal (target expression, or tautology in EQUATION mode). */
    isDerivationComplete(problem: MathProblem, steps: DerivationStep[] | undefined): boolean {
        if (!steps?.length) return false;
        const last = steps[steps.length - 1].resultExpression;
        if ((problem.goalMode ?? 'TRANSFORMATION') === 'EQUATION') {
            return isTautology(last);
        }
        const target = problem.targetExpression;
        return !!target && mathNodesEqual(last, target);
    }
}
