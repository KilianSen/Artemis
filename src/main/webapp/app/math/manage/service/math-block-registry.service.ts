import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

import { BlockDefinitionModel } from '../../shared/entities/block-definition.model';

@Injectable({ providedIn: 'root' })
export class MathBlockRegistryService {
    private http = inject(HttpClient);

    private readonly _blocks = signal<BlockDefinitionModel[]>([]);

    /** Read-only signal of all loaded block descriptors, consumed by pipes and components. */
    readonly blocks = this._blocks.asReadonly();

    /** Fetches the block registry and caches the result in the signal. */
    getBlockRegistry(): Observable<BlockDefinitionModel[]> {
        return this.http.get<BlockDefinitionModel[]>('api/math/block-registry').pipe(tap((result) => this._blocks.set(result)));
    }

    /**
     * Synchronous descriptor lookup for use in pipes and computed properties.
     *
     * `value` is the node's `value` field. For an `apply` node it holds the function name, and a block that
     * declares `functionName` wins the lookup — that is what lets an operator emitted as `apply("oplus", …)`
     * still render with its own infix symbol. Falls back to the plain type match (the generic `apply` block)
     * when no such block is registered, so unknown functions keep their juxtaposition rendering.
     */
    descriptorFor(type: string, value?: string): BlockDefinitionModel | undefined {
        const blocks = this._blocks();
        if (value !== undefined) {
            const byFunction = blocks.find((b) => b.functionName === value && b.functionName !== undefined);
            if (byFunction) {
                return byFunction;
            }
        }
        return blocks.find((b) => b.type === type);
    }
}
