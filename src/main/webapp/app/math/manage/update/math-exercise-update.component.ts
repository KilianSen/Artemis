import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathProblem } from 'app/math/shared/entities/math-problem.model';
import { MathExerciseService } from '../service/math-exercise.service';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { CategorySelectorPrimengComponent } from 'app/exercise/category-selector-primeng/category-selector-primeng.component';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import { MathProblemEditComponent } from './math-problem-edit/math-problem-edit.component';

@Component({
    selector: 'jhi-math-exercise-update',
    templateUrl: './math-exercise-update.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        CategorySelectorPrimengComponent,
        DifficultyPickerComponent,
        IncludedInOverallScorePickerComponent,
        MarkdownEditorMonacoComponent,
        FormDateTimePickerComponent,
        ArtemisTranslatePipe,
        MathProblemEditComponent,
        ButtonModule,
        CardModule,
        InputTextModule,
        TagModule,
        TextareaModule,
        TooltipModule,
    ],
})
export class MathExerciseUpdateComponent implements OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private mathExerciseService = inject(MathExerciseService);
    private exerciseService = inject(ExerciseService);
    private router = inject(Router);
    private profileService = inject(ProfileService);

    // eslint-disable-next-line localRules/prefer-signal-template-state -- template-driven form binds exercise sub-fields via [(ngModel)]; a signal cannot back two-way member writes
    mathExercise: MathExercise;
    readonly isSaving = signal(false);
    exerciseCategories = signal<ExerciseCategory[]>([]);
    existingCategories = signal<ExerciseCategory[]>([]);

    releaseDateField = viewChild<FormDateTimePickerComponent>('releaseDate');
    startDateField = viewChild<FormDateTimePickerComponent>('startDate');
    dueDateField = viewChild<FormDateTimePickerComponent>('dueDate');
    assessmentDateField = viewChild<FormDateTimePickerComponent>('assessmentDueDate');

    ngOnInit() {
        this.isSaving.set(false);
        this.activatedRoute.data.subscribe(({ mathExercise }) => {
            this.mathExercise = mathExercise;
            this.exerciseCategories.set(this.mathExercise.categories || []);
            if (!this.mathExercise.problems) {
                this.mathExercise.problems = [];
            }
        });
    }

    /** Max points is the sum of the individual problem points — displayed read-only and written to maxPoints on save. */
    get totalPoints(): number {
        return (this.mathExercise.problems ?? []).reduce((sum, problem) => sum + (problem.points ?? 0), 0);
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.mathExercise.categories = categories;
    }

    validateDate() {
        this.exerciseService.validateDate(this.mathExercise);
    }

    addProblem(): void {
        this.mathExercise.problems = [...(this.mathExercise.problems ?? []), new MathProblem()];
    }

    removeProblem(index: number): void {
        const updated = [...(this.mathExercise.problems ?? [])];
        updated.splice(index, 1);
        this.mathExercise.problems = updated;
    }

    moveProblemUp(index: number): void {
        if (index <= 0) {
            return;
        }
        const updated = [...(this.mathExercise.problems ?? [])];
        [updated[index - 1], updated[index]] = [updated[index], updated[index - 1]];
        this.mathExercise.problems = updated;
    }

    moveProblemDown(index: number): void {
        const problems = this.mathExercise.problems ?? [];
        if (index >= problems.length - 1) {
            return;
        }
        const updated = [...problems];
        [updated[index + 1], updated[index]] = [updated[index], updated[index + 1]];
        this.mathExercise.problems = updated;
    }

    // Dev-only JSON import/export tools — gated by the isDev getter (profileService.isDevelopment()).
    get isDev(): boolean {
        return this.profileService.isDevelopment();
    }

    isDragOver = signal(false);

    exportJson(): void {
        const json = JSON.stringify(this.mathExercise, null, 2);
        const blob = new Blob([json], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `math-exercise-${this.mathExercise.id ?? 'new'}.json`;
        a.click();
        URL.revokeObjectURL(url);
    }

    importJson(event: Event): void {
        const file = (event.target as HTMLInputElement).files?.[0];
        if (!file) return;
        this.readJsonFile(file);
        (event.target as HTMLInputElement).value = '';
    }

    onFileDrop(event: DragEvent): void {
        event.preventDefault();
        this.isDragOver.set(false);
        const file = event.dataTransfer?.files?.[0];
        if (file) this.readJsonFile(file);
    }

    private readJsonFile(file: File): void {
        const reader = new FileReader();
        reader.onload = () => {
            try {
                const parsed = JSON.parse(reader.result as string) as MathExercise;
                Object.assign(this.mathExercise, parsed);
                if (!this.mathExercise.problems) {
                    this.mathExercise.problems = [];
                }
            } catch {
                // malformed JSON — silently ignore in dev helper
            }
        };
        reader.readAsText(file);
    }

    save() {
        // Max points is derived from the sum of problem points so shared scoring keeps working.
        this.mathExercise.maxPoints = this.totalPoints;
        this.isSaving.set(true);
        if (this.mathExercise.id !== undefined) {
            this.mathExerciseService.update(this.mathExercise).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        } else {
            this.mathExerciseService.create(this.mathExercise).subscribe({
                next: () => this.onSaveSuccess(),
                error: () => this.onSaveError(),
            });
        }
    }

    previousState() {
        this.router.navigate(['course-management', this.mathExercise.course?.id || this.activatedRoute.snapshot.params['courseId'], 'math-exercises']);
    }

    private onSaveSuccess() {
        this.isSaving.set(false);
        this.previousState();
    }

    private onSaveError() {
        this.isSaving.set(false);
    }
}
