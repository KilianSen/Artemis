import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { MathProblemAnswer } from './math-problem-answer.model';
import { MathExercise } from './math-exercise.model';

export interface MathParticipation {
    id?: number;
    studentLogin?: string;
    studentName?: string;
    exercise?: MathExercise;
}

/** Async grading state of a remotely-graded submission. REVIEW/FAILED mean it was escalated to manual tutor review. */
export type MathGradingState = 'PENDING' | 'COMPLETED' | 'REVIEW' | 'FAILED';

/** Websocket payload pushed when a submission's grading settles to a terminal state with no automatic result. */
export interface MathGradingStatusMessage {
    submissionId?: number;
    participationId?: number;
    status?: MathGradingState;
}

export class MathSubmission extends Submission {
    /** The student's per-problem answers, each carrying its ordered derivation steps. */
    public answers?: MathProblemAnswer[];
    /** Current async grading state (PENDING/REVIEW/FAILED); undefined for in-process grading or before submit. */
    public gradingState?: MathGradingState;
    declare participation?: MathParticipation;

    constructor() {
        super(SubmissionExerciseType.MATH);
    }
}
