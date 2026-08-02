import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { BlockDefinitionModel } from 'app/math/shared/entities/block-definition.model';

describe('MathBlockRegistryService', () => {
    setupTestBed({ zoneless: true });

    let service: MathBlockRegistryService;
    let httpMock: HttpTestingController;

    const sample: BlockDefinitionModel[] = [
        { type: 'add', displaySymbol: '+', layoutCategory: 'BINARY_INFIX' } as unknown as BlockDefinitionModel,
        { type: 'num', displaySymbol: 'n', layoutCategory: 'LEAF' } as unknown as BlockDefinitionModel,
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), MathBlockRegistryService],
        });
        service = TestBed.inject(MathBlockRegistryService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('loads the block registry and caches it in the signal', async () => {
        const promise = firstValueFrom(service.getBlockRegistry());
        const req = httpMock.expectOne({ method: 'GET', url: 'api/math/block-registry' });
        req.flush(sample);
        await promise;

        expect(service.blocks()).toEqual(sample);
    });

    it('resolves descriptorFor by type after load', async () => {
        const promise = firstValueFrom(service.getBlockRegistry());
        httpMock.expectOne({ method: 'GET', url: 'api/math/block-registry' }).flush(sample);
        await promise;

        expect(service.descriptorFor('add')?.displaySymbol).toBe('+');
    });

    it('returns undefined for unknown block types', () => {
        expect(service.descriptorFor('mystery')).toBeUndefined();
    });

    // The registry<->apply bridge: an operator declared with `functionName` travels as the generic
    // `apply` node (which every grading backend already understands) but must still resolve to its own
    // descriptor so it renders as an operator rather than as a function call.
    describe('descriptorFor with an apply function name', () => {
        const oplus = { type: 'oplus', category: 'arithmetic', label: 'Oplus', paletteLatex: 'a \\oplus b', functionName: 'oplus', latexSymbol: '\\oplus' };

        async function load(blocks: unknown[]): Promise<void> {
            const promise = firstValueFrom(service.getBlockRegistry());
            httpMock.expectOne({ method: 'GET', url: 'api/math/block-registry' }).flush(blocks);
            await promise;
        }

        it('resolves an apply node by its function name', async () => {
            await load([...sample, oplus]);
            expect(service.descriptorFor('apply', 'oplus')?.latexSymbol).toBe('\\oplus');
        });

        it('falls back to the plain type when no block claims the function name', async () => {
            await load([...sample, oplus]);
            // `fact` is an ordinary function, not a declared operator -> the generic apply block (or nothing).
            expect(service.descriptorFor('apply', 'fact')?.functionName).toBeUndefined();
        });

        it('does not match blocks that declare no functionName when value is undefined', async () => {
            await load([...sample, oplus]);
            expect(service.descriptorFor('add')?.type).toBe('add');
        });
    });
});
