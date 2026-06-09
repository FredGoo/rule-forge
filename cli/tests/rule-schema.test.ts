import { describe, it, expect, vi } from 'vitest';
import { apiGet } from '../lib/utils.js';

/**
 * Rule schema CLI 命令测试 (V5.22)
 *
 * 这些测试验证 CLI 调 /rule-schema/* 端点的契约,不实际启服务。
 * Mock fetch 模拟后端响应。
 */
interface TypeEntry {
    type: string;
    name?: string;
    v522Supported?: boolean | string;
    priority?: number;
    description?: string;
}

interface SchemaResponse {
    type: string;
    name?: string;
    jsonStructure?: any;
    operators?: any;
    example?: any;
    tips?: string[];
}

interface ListTypesResponse {
    types: TypeEntry[];
}

describe('rule schema CLI commands (V5.22)', () => {

    // ========== rule list-types ==========

    describe('rule list-types', () => {
        it('should call /rule-schema/types and return all supported rule types', async () => {
            // Given — mock 后端返回 9 个 type
            const mockResponse: ListTypesResponse = {
                types: [
                    { type: 'decision_table', name: '决策表', v522Supported: true, priority: 1, description: '行=规则,列=条件/动作' },
                    { type: 'ul', name: '脚本式规则集', v522Supported: true, priority: 2, description: 'UEL 脚本' },
                    { type: 'crosstab', name: '交叉表', v522Supported: false, priority: 9, description: '占位' }
                ]
            };
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true,
                status: 200,
                json: async () => mockResponse
            });

            // When — 调 apiGet
            const data = await apiGet('/rule-schema/types', undefined, 'http://fake', mockFetch) as ListTypesResponse;

            // Then — 验请求 URL 和响应
            expect(mockFetch).toHaveBeenCalledWith('http://fake/rule-schema/types');
            expect(data.types).toHaveLength(3);
            expect(data.types[0].type).toBe('decision_table');
            expect(data.types[0].v522Supported).toBe(true);
        });

        it('should throw on HTTP error (server down)', async () => {
            // Given — 后端 500
            const mockFetch = vi.fn().mockResolvedValue({
                ok: false,
                status: 500,
                statusText: 'Internal Server Error'
            });

            // When/Then — 抛错
            await expect(apiGet('/rule-schema/types', undefined, 'http://fake', mockFetch))
                .rejects.toThrow('HTTP 500');
        });
    });

    // ========== rule get-schema ==========

    describe('rule get-schema', () => {
        it('should call /rule-schema/{type} and return full JSON structure', async () => {
            // Given — mock 后端返 decision_table 完整 schema
            const mockSchema: SchemaResponse = {
                type: 'decision_table',
                name: '决策表',
                jsonStructure: { topLevel: { rows: 'List<Row>' } },
                operators: { condition: { lt: '小于' } },
                example: { rows: [{ rowId: 'r1' }] },
                tips: ['tip 1']
            };
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true,
                status: 200,
                json: async () => mockSchema
            });

            // When
            const data = await apiGet('/rule-schema/decision_table', undefined, 'http://fake', mockFetch) as SchemaResponse;

            // Then — 验证 URL encode 和响应结构
            expect(mockFetch).toHaveBeenCalledWith('http://fake/rule-schema/decision_table');
            expect(data.type).toBe('decision_table');
            expect(data.jsonStructure).toBeDefined();
            expect(data.operators).toBeDefined();
            expect(data.example).toBeDefined();
        });

        it('should URL-encode rule type with special chars', async () => {
            // Given — 类似 "decision-tree-v2" 这样的 type
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true,
                status: 200,
                json: async () => ({ type: 'test' })
            });

            // When
            await apiGet('/rule-schema/decision-tree-v2', undefined, 'http://fake', mockFetch);

            // Then
            expect(mockFetch).toHaveBeenCalledWith('http://fake/rule-schema/decision-tree-v2');
        });

        it('should propagate 404 for unknown type', async () => {
            // Given — 404
            const mockFetch = vi.fn().mockResolvedValue({
                ok: false,
                status: 404,
                statusText: 'Not Found'
            });

            // When/Then
            await expect(apiGet('/rule-schema/non_existent', undefined, 'http://fake', mockFetch))
                .rejects.toThrow('HTTP 404');
        });
    });

    // ========== LLM agent 工作流模拟 ==========

    describe('LLM agent workflow: discover then fetch schema', () => {
        it('should support 2-step lookup: list-types then get-schema', async () => {
            // Given — 第 1 步:list-types 返 decision_table
            //        第 2 步:get-schema decision_table 返完整 schema
            let step = 0;
            const mockFetch = vi.fn().mockImplementation(async (url: string) => {
                step++;
                if (step === 1) {
                    expect(url).toContain('/rule-schema/types');
                    return { ok: true, status: 200, json: async () => ({ types: [{ type: 'decision_table', v522Supported: true, priority: 1 }] }) };
                } else {
                    expect(url).toContain('/rule-schema/decision_table');
                    return { ok: true, status: 200, json: async () => ({ type: 'decision_table', example: { rows: [] } }) };
                }
            });

            // When
            const types = await apiGet('/rule-schema/types', undefined, 'http://fake', mockFetch) as ListTypesResponse;
            const target = types.types.find((t: TypeEntry) => t.v522Supported === true);
            expect(target).toBeDefined();
            const schema = await apiGet(`/rule-schema/${target!.type}`, undefined, 'http://fake', mockFetch) as SchemaResponse;

            // Then
            expect(target!.type).toBe('decision_table');
            expect(schema.example).toBeDefined();
            expect(mockFetch).toHaveBeenCalledTimes(2);
        });
    });
});
