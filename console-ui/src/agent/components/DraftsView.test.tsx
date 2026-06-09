import {describe, it, expect, vi, beforeEach} from 'vitest';
import {render, screen, waitFor, fireEvent} from '@testing-library/react';
import DraftsView from './DraftsView';

// Mock the API client
vi.mock('@/api/client', () => ({
    jsonPost: vi.fn(),
}));

import {jsonPost} from '@/api/client';
const mockJsonPost = jsonPost as unknown as ReturnType<typeof vi.fn>;

// Mock window._server and window.confirm/prompt
beforeEach(() => {
    (global as any).window._server = 'http://test';
    (global as any).window.confirm = vi.fn(() => true);
    (global as any).window.prompt = vi.fn(() => 'test-package');
    (global as any).window.alert = vi.fn();
    mockJsonPost.mockReset();
});

describe('DraftsView (V5.22)', () => {
    it('should call list_drafts on mount and show results', async () => {
        // Given
        const mockDrafts = {
            count: 2,
            drafts: [
                {
                    draftId: 'drf_a', ruleType: 'decision_table', project: 'demo',
                    status: 'DRAFT', title: '年龄拒贷', source: 'LLM',
                    createdBy: 'user1', createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                    content: {type: 'decision_table', rows: []},
                },
                {
                    draftId: 'drf_b', ruleType: 'ul', project: 'demo',
                    status: 'PENDING_REVIEW', title: '收入门槛', source: 'LLM',
                    createdBy: 'user1', createdAt: '2026-06-09T11:00:00', updatedAt: '2026-06-09T11:00:00',
                    content: {type: 'ul', rules: []},
                },
            ]
        };
        mockJsonPost.mockResolvedValueOnce(mockDrafts);

        // When
        render(<DraftsView project="demo" username="BA1" />);

        // Then
        await waitFor(() => {
            expect(screen.getByText('年龄拒贷')).toBeDefined();
            expect(screen.getByText('收入门槛')).toBeDefined();
        });
        // Status badges — 出现 2 次(下拉 option + 列表 badge)
        expect(screen.getAllByText('草稿').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('待审批').length).toBeGreaterThanOrEqual(1);
        // API call
        expect(mockJsonPost).toHaveBeenCalledWith(
            '/agent/tools/list_drafts',
            expect.objectContaining({project: 'demo', limit: 50}),
            expect.objectContaining({silent: true})
        );
    });

    it('should show empty state when no drafts', async () => {
        mockJsonPost.mockResolvedValueOnce({count: 0, drafts: []});
        render(<DraftsView project="empty" />);
        await waitFor(() => {
            expect(screen.getByText(/暂无草稿/)).toBeDefined();
        });
    });

    it('should show error message on API failure', async () => {
        mockJsonPost.mockRejectedValueOnce(new Error('Network error'));
        render(<DraftsView project="demo" />);
        await waitFor(() => {
            expect(screen.getByText(/加载草稿失败/)).toBeDefined();
        });
    });

    it('should call submit_draft when clicking 提交审批 button on DRAFT', async () => {
        // Given — list returns one DRAFT with unique title
        mockJsonPost
            .mockResolvedValueOnce({
                count: 1,
                drafts: [{
                    draftId: 'drf_a', ruleType: 'decision_table', project: 'demo',
                    status: 'DRAFT', title: '年龄拒贷测试', source: 'LLM', createdBy: 'u',
                    createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                    content: {},
                }]
            })
            // click detail
            .mockResolvedValueOnce({
                draftId: 'drf_a', ruleType: 'decision_table', project: 'demo',
                status: 'DRAFT', title: '年龄拒贷测试', source: 'LLM', createdBy: 'u',
                createdAt: '2026-06-09T10:00:00', updatedAt: '2026-06-09T10:00:00',
                content: {type: 'decision_table', rows: []},
            })
            // submit
            .mockResolvedValueOnce({status: 'PENDING_REVIEW'});

        // When
        render(<DraftsView project="demo" username="BA1" />);
        await waitFor(() => screen.getByText('年龄拒贷测试'));
        // Click on draft to open detail
        fireEvent.click(screen.getByText('年龄拒贷测试'));
        await waitFor(() => screen.getByText(/提交审批/));
        fireEvent.click(screen.getByText(/提交审批/));

        // Then
        await waitFor(() => {
            const submitCall = mockJsonPost.mock.calls.find(
                c => c[0] === '/agent/tools/submit_draft'
            );
            expect(submitCall).toBeDefined();
            expect(submitCall[1]).toMatchObject({draftId: 'drf_a', submittedBy: 'BA1'});
        });
    });
});
