import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { createRequestOption } from 'app/foundation/util/request.util';
import { MathNode } from 'app/math/shared/entities/math-node.model';
import { HintSuggestion } from 'app/math/shared/entities/hint-suggestion.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';

export type EntityResponseType = HttpResponse<MathSubmission>;

@Injectable({ providedIn: 'root' })
export class MathSubmissionService {
    private http = inject(HttpClient);
    private submissionService = inject(SubmissionService);

    getDataForMathEditor(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<MathSubmission>(`api/math/participations/${participationId}/math-editor`, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    create(mathSubmission: MathSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(mathSubmission);
        return this.http
            .post<MathSubmission>(`api/math/exercises/${exerciseId}/math-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    update(mathSubmission: MathSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(mathSubmission);
        return this.http
            .put<MathSubmission>(`api/math/exercises/${exerciseId}/math-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    getMathSubmission(submissionId: number): Observable<MathSubmission> {
        return this.http
            .get<MathSubmission>(`api/math/math-submissions/${submissionId}`, {
                observe: 'response',
            })
            .pipe(
                map((res: HttpResponse<MathSubmission>) => {
                    if (!res.body) {
                        throw new Error('Empty response body for getMathSubmission');
                    }
                    return res.body;
                }),
            );
    }

    getMathSubmissionForAssessment(submissionId: number): Observable<MathSubmission> {
        return this.http
            .get<MathSubmission>(`api/math/math-submissions/${submissionId}/for-assessment`, {
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<MathSubmission>) => res.body!));
    }

    getSubmittedSubmissions(exerciseId: number): Observable<MathSubmission[]> {
        return this.http
            .get<MathSubmission[]>(`api/math/exercises/${exerciseId}/math-submissions`, { observe: 'response' })
            .pipe(map((res: HttpResponse<MathSubmission[]>) => res.body ?? []));
    }

    /**
     * Submissions for the assessment dashboard. With {@code assessedByTutor} returns the current tutor's assessed
     * submissions; otherwise all submitted submissions. Returns the full response (the dashboard reads its body).
     */
    getSubmissions(exerciseId: number, req: { submittedOnly?: boolean; assessedByTutor?: boolean }, correctionRound = 0): Observable<HttpResponse<MathSubmission[]>> {
        let params = createRequestOption(req);
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        return this.http
            .get<MathSubmission[]>(`api/math/exercises/${exerciseId}/math-submissions`, { params, observe: 'response' })
            .pipe(map((res: HttpResponse<MathSubmission[]>) => this.submissionService.convertArrayResponse(res)));
    }

    /** The next submission eligible for manual assessment (auto-grading was inconclusive), optionally locked to the current tutor. */
    getSubmissionWithoutAssessment(exerciseId: number, lock?: boolean, correctionRound = 0): Observable<MathSubmission | undefined> {
        let params = new HttpParams();
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        if (lock) {
            params = params.set('lock', 'true');
        }
        return this.http
            .get<MathSubmission | undefined>(`api/math/exercises/${exerciseId}/math-submission-without-assessment`, { params })
            .pipe(map((res?: MathSubmission) => res ?? undefined));
    }

    /**
     * Records the tutor's manual score + feedback. Pass {@code submit=true} to finalize the assessment, or
     * {@code false} (default) to save a draft that keeps the submission locked and hidden from the student.
     */
    saveManualResult(submissionId: number, score: number, feedbacks: Feedback[] = [], submit = false): Observable<MathSubmission> {
        const params = new HttpParams().set('submit', submit.toString());
        return this.http
            .put<MathSubmission>(`api/math/math-submissions/${submissionId}/manual-result`, { score, feedbacks }, { params, observe: 'response' })
            .pipe(map((res: HttpResponse<MathSubmission>) => res.body!));
    }

    /** Cancels an in-progress assessment, releasing the tutor's soft lock on the submission. */
    cancelAssessment(submissionId: number): Observable<void> {
        return this.http.put<void>(`api/math/math-submissions/${submissionId}/cancel-assessment`, undefined);
    }

    /** Resolves a student complaint and applies the tutor's revised score + feedback. */
    updateAssessmentAfterComplaint(submissionId: number, score: number, feedbacks: Feedback[], complaintResponse: ComplaintResponse): Observable<MathSubmission> {
        return this.http
            .put<MathSubmission>(`api/math/math-submissions/${submissionId}/assessment-after-complaint`, { score, feedbacks, complaintResponse }, { observe: 'response' })
            .pipe(map((res: HttpResponse<MathSubmission>) => res.body!));
    }

    /** Asks the backend for ranked next-step suggestions for a specific problem at the current math state. */
    getHints(exerciseId: number, problemId: number, currentExpression: MathNode): Observable<HintSuggestion[]> {
        return this.http
            .post<HintSuggestion[]>(`api/math/exercises/${exerciseId}/problems/${problemId}/hints`, { currentExpression }, { observe: 'response' })
            .pipe(map((res: HttpResponse<HintSuggestion[]>) => res.body ?? []));
    }
}
