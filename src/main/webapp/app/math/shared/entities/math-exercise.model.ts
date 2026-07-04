import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { MathProblem } from './math-problem.model';

export class MathExercise extends Exercise {
    public exampleSolution?: string;
    public description?: string;
    /** The ordered list of math problems (questions) this exercise holds. */
    public problems?: MathProblem[];

    constructor(course: Course | undefined) {
        super(ExerciseType.MATH);
        this.course = course;
        this.problems = [];
    }
}
