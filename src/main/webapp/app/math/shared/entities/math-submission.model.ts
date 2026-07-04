import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { MathProblemAnswer } from './math-problem-answer.model';
import { MathExercise } from './math-exercise.model';

export interface MathParticipation {
    id?: number;
    studentLogin?: string;
    studentName?: string;
    exercise?: MathExercise;
}

export class MathSubmission extends Submission {
    /** The student's per-problem answers, each carrying its ordered derivation steps. */
    public answers?: MathProblemAnswer[];
    declare participation?: MathParticipation;

    constructor() {
        super(SubmissionExerciseType.MATH);
    }
}
