import { describe, it, expect, vi } from 'vitest';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { apiPost } from '../lib/utils.js';

/**
 * V5.22 — AI 规则草稿 CLI 命令测试
 *
 * 这些命令是给 LLM agent 用的:
 *   - ruleforge rule draft
 *   - ruleforge rule list-drafts
 *   - ruleforge rule get-draft
 *   - ruleforge rule submit / approve / reject / apply
 *   - ruleforge rule test-gen / test-run
 *
 * 不实际启后端,Mock fetch 模拟响应。
 */
describe('V5.22 draft CLI commands (LLM agent)', () => {

    // ========== rule draft ==========

    describe('rule draft', () => {
        it('should POST /agent/tools/draft_rule with ruleType/project/content', async () => {
            // Given
            const mockResponse = {
                draftId: 'drf_abc123',
                status: 'DRAFT',
                ruleType: 'decision_table',
                project: 'demo',
                title: null,
                message: '草稿已创建'
            };
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true,
                status: 200,
                text: async () => JSON.stringify(mockResponse)
            });

            // When
            const data = await apiPost('/agent/tools/draft_rule', {
                ruleType: 'decision_table',
                project: 'demo',
                content: '{"type":"decision_table","rows":[],"columns":[],"cellMap":{}}',
                createdBy: 'LLM',
                sessionId: 'sess-1'
            }, 'http://fake', mockFetch);

            // Then
            expect(mockFetch).toHaveBeenCalledWith('http://fake/agent/tools/draft_rule', expect.objectContaining({
                method: 'POST',
                headers: { 'Content-Type': 'application/json' }
            }));
            const body = JSON.parse(mockFetch.mock.calls[0][1].body);
            expect(body.ruleType).toBe('decision_table');
            expect(body.project).toBe('demo');
            expect(body.content).toContain('decision_table');
            expect(data.draftId).toBe('drf_abc123');
        });

        it('should read content from --content-file when provided', async () => {
            // Given — 写一个临时文件
            const tmpFile = path.join(os.tmpdir(), `rule-${Date.now()}.json`);
            const fileContent = '{"type":"decision_table","rows":[],"columns":[],"cellMap":{}}';
            fs.writeFileSync(tmpFile, fileContent);

            try {
                const mockFetch = vi.fn().mockResolvedValue({
                    ok: true,
                    status: 200,
                    text: async () => JSON.stringify({ draftId: 'drf_xyz', status: 'DRAFT' })
                });

                // When
                const data = await apiPost('/agent/tools/draft_rule', {
                    ruleType: 'decision_table',
                    project: 'demo',
                    content: fs.readFileSync(tmpFile, 'utf8')
                }, 'http://fake', mockFetch);

                // Then
                expect(data.draftId).toBe('drf_xyz');
                const body = JSON.parse(mockFetch.mock.calls[0][1].body);
                expect(body.content).toBe(fileContent);
            } finally {
                fs.unlinkSync(tmpFile);
            }
        });
    });

    // ========== list-drafts ==========

    describe('rule list-drafts', () => {
        it('should POST /agent/tools/list_drafts with project/status filter', async () => {
            // Given
            const mockResponse = {
                drafts: [
                    { draftId: 'drf_a', status: 'DRAFT' },
                    { draftId: 'drf_b', status: 'PENDING_REVIEW' }
                ],
                count: 2
            };
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200, text: async () => JSON.stringify(mockResponse)
            });

            // When
            const data = await apiPost('/agent/tools/list_drafts', {
                project: 'demo', status: 'DRAFT', limit: 10
            }, 'http://fake', mockFetch);

            // Then
            const body = JSON.parse(mockFetch.mock.calls[0][1].body);
            expect(body.project).toBe('demo');
            expect(body.status).toBe('DRAFT');
            expect(body.limit).toBe(10);
            expect(data.count).toBe(2);
        });
    });

    // ========== 状态机 ==========

    describe('draft lifecycle', () => {
        it('submit → POST submit_draft', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200, text: async () => JSON.stringify({ draftId: 'd1', status: 'PENDING_REVIEW' })
            });
            const data = await apiPost('/agent/tools/submit_draft', { draftId: 'd1', submittedBy: 'u' }, 'http://fake', mockFetch);
            expect(data.status).toBe('PENDING_REVIEW');
        });

        it('approve → POST approve_draft with reviewer/comment', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200, text: async () => JSON.stringify({ draftId: 'd1', status: 'APPROVED' })
            });
            const data = await apiPost('/agent/tools/approve_draft', {
                draftId: 'd1', reviewer: 'r1', comment: 'ok'
            }, 'http://fake', mockFetch);
            expect(data.status).toBe('APPROVED');
        });

        it('reject → POST reject_draft with reason', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200, text: async () => JSON.stringify({ draftId: 'd1', status: 'REJECTED' })
            });
            const data = await apiPost('/agent/tools/reject_draft', {
                draftId: 'd1', reviewer: 'r1', reason: 'cell r1,c2 错'
            }, 'http://fake', mockFetch);
            expect(data.status).toBe('REJECTED');
        });

        it('apply → POST apply_draft with packagePath/fileName/reviewer', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200, text: async () => JSON.stringify({
                    draftId: 'd1', packagePath: '/pkg', newVersion: 'v1.0.5', fileName: 'rule_dt_d1.json'
                })
            });
            const data = await apiPost('/agent/tools/apply_draft', {
                draftId: 'd1', packagePath: '/pkg', fileName: 'rule_dt_d1.json', reviewer: 'r1', versionComment: 'first apply'
            }, 'http://fake', mockFetch);
            expect(data.newVersion).toBe('v1.0.5');
        });
    });

    // ========== 测试 ==========

    describe('rule test-gen / test-run', () => {
        it('test-gen → POST generate_test_cases with draftId/count', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200, text: async () => JSON.stringify({
                    draftId: 'd1', count: 2, testCases: [{ name: 'auto_r1' }]
                })
            });
            const data = await apiPost('/agent/tools/generate_test_cases', {
                draftId: 'd1', count: 5
            }, 'http://fake', mockFetch);
            expect(data.count).toBe(2);
        });

        it('test-run → POST run_test with draftId + testCases array (not string)', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200, text: async () => JSON.stringify({
                    draftId: 'd1', passed: 1, failed: 0,
                    results: [{ name: 'tc1', matchedRowId: 'r1', status: 'PASS' }]
                })
            });
            const data = await apiPost('/agent/tools/run_test', {
                draftId: 'd1',
                testCases: [{ name: 'tc1', rowId: 'r1', inputs: { age: 17 } }]
            }, 'http://fake', mockFetch);
            expect(data.passed).toBe(1);
            // 验传给后端的 testCases 是 array 不是 string
            const body = JSON.parse(mockFetch.mock.calls[0][1].body);
            expect(Array.isArray(body.testCases)).toBe(true);
            expect(body.testCases[0].name).toBe('tc1');
        });
    });

    // ========== LLM agent 工作流模拟 ==========

    describe('LLM agent end-to-end workflow', () => {
        it('draft → test-gen → test-run (3 POST calls)', async () => {
            // 模拟 3 步:create draft, gen tests, run tests
            const mockFetch = vi.fn().mockImplementation(async (url: string) => {
                if (url.includes('/agent/tools/draft_rule')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ draftId: 'drf_a', status: 'DRAFT' }) };
                } else if (url.includes('/agent/tools/generate_test_cases')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ count: 2, testCases: [{ name: 'tc1' }, { name: 'tc2' }] }) };
                } else if (url.includes('/agent/tools/run_test')) {
                    return { ok: true, status: 200, text: async () => JSON.stringify({ passed: 2, failed: 0 }) };
                }
                throw new Error('Unexpected URL: ' + url);
            });

            // 1. create draft
            const draft = await apiPost('/agent/tools/draft_rule', {
                ruleType: 'decision_table', project: 'demo', content: '{}'
            }, 'http://fake', mockFetch) as any;
            expect(draft.draftId).toBe('drf_a');
            // 2. generate tests
            const tests = await apiPost('/agent/tools/generate_test_cases', { draftId: 'drf_a' }, 'http://fake', mockFetch) as any;
            expect(tests.count).toBe(2);
            // 3. run tests
            const run = await apiPost('/agent/tools/run_test', {
                draftId: 'drf_a', testCases: tests.testCases
            }, 'http://fake', mockFetch) as any;
            expect(run.passed).toBe(2);

            expect(mockFetch).toHaveBeenCalledTimes(3);
        });
    });

    // ========== 错误处理 ==========

    describe('error handling', () => {
        it('should throw on HTTP 500', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: false, status: 500, statusText: 'Internal Server Error', text: async () => 'oops'
            });
            await expect(apiPost('/agent/tools/draft_rule', {}, 'http://fake', mockFetch))
                .rejects.toThrow('HTTP 500');
        });

        it('should return null on empty response body', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200, text: async () => ''
            });
            const data = await apiPost('/agent/tools/apply_draft', {}, 'http://fake', mockFetch);
            expect(data).toBeNull();
        });
    });

    // ========== V5.22.1 持久化测试用例 ==========

    describe('persisted test cases (V5.22.1)', () => {
        it('list-tests → POST list_test_cases with draftId', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200,
                text: async () => JSON.stringify({
                    draftId: 'd1', count: 2,
                    testCases: [
                        {testCaseId: 'tc_1', name: 'under18', expectedRowId: 'r1'},
                        {testCaseId: 'tc_2', name: 'normalAdult', expectedRowId: null},
                    ]
                })
            });
            const data = await apiPost('/agent/tools/list_test_cases', {draftId: 'd1'}, 'http://fake', mockFetch);
            expect(data.count).toBe(2);
            expect(data.testCases[0].name).toBe('under18');
        });

        it('add-test → POST add_test_case with name/inputs/expectedRowId', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200,
                text: async () => JSON.stringify({
                    testCaseId: 'tc_new', draftId: 'd1', name: 'under18',
                    expectedRowId: 'r1', source: 'MANUAL'
                })
            });
            const data = await apiPost('/agent/tools/add_test_case', {
                draftId: 'd1',
                name: 'under18',
                inputs: '{"customer.age":17}',
                expectedRowId: 'r1',
                createdBy: 'BA1',
                source: 'MANUAL',
            }, 'http://fake', mockFetch);
            expect(data.testCaseId).toBe('tc_new');
            expect(data.name).toBe('under18');
            // 验证请求体包含所有字段
            const body = JSON.parse(mockFetch.mock.calls[0][1].body);
            expect(body.draftId).toBe('d1');
            expect(body.inputs).toBe('{"customer.age":17}');
            expect(body.expectedRowId).toBe('r1');
        });

        it('del-test → POST delete_test_case with testCaseId', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200,
                text: async () => JSON.stringify({testCaseId: 'tc_1', deleted: true})
            });
            const data = await apiPost('/agent/tools/delete_test_case', {testCaseId: 'tc_1'}, 'http://fake', mockFetch);
            expect(data.deleted).toBe(true);
        });

        it('run-saved-tests → POST run_saved_tests with draftId', async () => {
            const mockFetch = vi.fn().mockResolvedValue({
                ok: true, status: 200,
                text: async () => JSON.stringify({
                    draftId: 'd1', passed: 1, failed: 0, total: 1,
                    results: [{testCaseId: 'tc_1', name: 'under18', expectedRowId: 'r1', matchedRowId: 'r1', status: 'PASS'}]
                })
            });
            const data = await apiPost('/agent/tools/run_saved_tests', {draftId: 'd1'}, 'http://fake', mockFetch);
            expect(data.passed).toBe(1);
            expect(data.results[0].status).toBe('PASS');
        });

        it('LLM agent workflow: add-test + run-saved-tests (2 POST calls)', async () => {
            const mockFetch = vi.fn().mockImplementation(async (url: string) => {
                if (url.includes('/agent/tools/add_test_case')) {
                    return {ok: true, status: 200, text: async () => JSON.stringify({testCaseId: 'tc_x'})};
                } else if (url.includes('/agent/tools/run_saved_tests')) {
                    return {ok: true, status: 200, text: async () => JSON.stringify({passed: 1, failed: 0, total: 1})};
                }
                throw new Error('Unexpected URL: ' + url);
            });

            // 1. add a test
            const added = await apiPost('/agent/tools/add_test_case', {
                draftId: 'd1', name: 'tc1', inputs: '{"x":1}', expectedRowId: 'r1', createdBy: 'LLM'
            }, 'http://fake', mockFetch) as any;
            expect(added.testCaseId).toBe('tc_x');

            // 2. run saved
            const run = await apiPost('/agent/tools/run_saved_tests', {draftId: 'd1'}, 'http://fake', mockFetch) as any;
            expect(run.passed).toBe(1);

            expect(mockFetch).toHaveBeenCalledTimes(2);
        });
    });
});
