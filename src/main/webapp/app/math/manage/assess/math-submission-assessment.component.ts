import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgClass } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';
import { DerivationStep } from 'app/math/shared/entities/derivation-step.model';
import { mathNodesEqual } from 'app/math/shared/entities/math-node.model';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { MathNodeLatexPipe } from 'app/math/shared/math-node-latex.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';

@Component({
    selector: 'jhi-math-submission-assessment',
    templateUrl: './math-submission-assessment.component.html',
    imports: [
        AssessmentLayoutComponent,
        TranslateDirective,
        HtmlForMarkdownPipe,
        MathNodeLatexPipe,
        NgClass,
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

    ngOnInit() {
        this.route.data.subscribe(({ mathSubmission }) => {
            if (mathSubmission) {
                this.submission.set(mathSubmission as MathSubmission);
                this.mathExercise.set(this.submission().participation?.exercise as MathExercise);
                this.result.set(this.submission().results?.[this.submission().results!.length - 1]);
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
        this.mathSubmissionService.saveManualResult(this.submission().id!, this.manualScore).subscribe({
            next: (updated) => {
                this.result.set(updated.results?.[updated.results.length - 1]);
                this.saveBusy.set(false);
                this.saveSuccess.set(true);
            },
            error: () => {
                this.saveBusy.set(false);
            },
        });
    }

    get hasAstExpressions(): boolean {
        return !!(this.mathExercise()?.sourceExpression && this.mathExercise()?.targetExpression);
    }

    get hasExampleDerivations(): boolean {
        return !!this.mathExercise()?.exampleDerivations?.length;
    }

    isExampleComplete(derivation: DerivationStep[]): boolean {
        const target = this.mathExercise()?.targetExpression;
        if (!derivation?.length || !target) return false;
        return mathNodesEqual(derivation[derivation.length - 1].resultExpression, target);
    }

    get isMathComplete(): boolean {
        const steps = this.submission()?.steps;
        const target = this.mathExercise()?.targetExpression;
        if (!steps?.length || !target) return false;
        return mathNodesEqual(steps[steps.length - 1].resultExpression, target);
    }
}
