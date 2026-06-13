import { describe, expect, it } from 'vitest';
import {
    DEFAULT_DIALECT,
    detectPmmlDialectFromFilePath,
    pmmlDialectLabel,
    type PmmlDialect,
} from './pmmlDialect';

describe('V5.41.6 — PmmlDialect (console-ui side)', () => {
    describe('detectPmmlDialectFromFilePath', () => {
        it('returns PMML for .pmml paths', () => {
            expect(detectPmmlDialectFromFilePath('rules/customer-score.pmml')).toBe('PMML');
            expect(detectPmmlDialectFromFilePath('rules/loan-classifier.PMML')).toBe('PMML');
        });

        it('returns RULEFORGE_NATIVE for .xml paths', () => {
            expect(detectPmmlDialectFromFilePath('rules/customer-score.xml')).toBe('RULEFORGE_NATIVE');
            expect(detectPmmlDialectFromFilePath('rules/loan-classifier.XML')).toBe('RULEFORGE_NATIVE');
        });

        it('returns RULEFORGE_NATIVE for paths without recognized extension', () => {
            expect(detectPmmlDialectFromFilePath('rules/foo.dmn')).toBe('RULEFORGE_NATIVE');
            expect(detectPmmlDialectFromFilePath('rules/foo.drl')).toBe('RULEFORGE_NATIVE');
        });

        it('returns DEFAULT_DIALECT for null/empty', () => {
            expect(detectPmmlDialectFromFilePath('')).toBe(DEFAULT_DIALECT);
            // intentional: testing null/undefined runtime safety (JS callers may pass these)
            expect(detectPmmlDialectFromFilePath(null as unknown as string)).toBe(DEFAULT_DIALECT);
            expect(detectPmmlDialectFromFilePath(undefined as unknown as string)).toBe(DEFAULT_DIALECT);
        });
    });

    describe('pmmlDialectLabel', () => {
        it('returns human label for PMML', () => {
            expect(pmmlDialectLabel('PMML')).toContain('PMML 4.4');
        });

        it('returns human label for RULEFORGE_NATIVE', () => {
            expect(pmmlDialectLabel('RULEFORGE_NATIVE')).toContain('RuleForge XML');
        });

        it('returns default label for null/undefined', () => {
            expect(pmmlDialectLabel(null)).toContain('RuleForge XML');
            expect(pmmlDialectLabel(undefined)).toContain('RuleForge XML');
        });
    });

    it('default dialect is RULEFORGE_NATIVE (V5.40-and-earlier compat)', () => {
        expect(DEFAULT_DIALECT).toBe<PmmlDialect>('RULEFORGE_NATIVE');
    });
});
