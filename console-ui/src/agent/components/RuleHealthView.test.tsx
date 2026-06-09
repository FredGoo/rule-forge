import {describe, it, expect, vi, beforeEach} from 'vitest';
import {render, screen, waitFor} from '@testing-library/react';
import RuleHealthView from './RuleHealthView';

vi.mock('@/api/client', () => ({
    jsonPost: vi.fn(),
}));

import {jsonPost} from '@/api/client';
const mockJsonPost = jsonPost as unknown as ReturnType<typeof vi.fn>;

beforeEach(() => {
    (global as any).window._server = 'http://test';
    mockJsonPost.mockReset();
});

describe('RuleHealthView (V5.22.2)', () => {
    it('should load health data and display coverage + stale drafts', async () => {
        mockJsonPost.mockResolvedValueOnce({
            project: 'demo', days: 30, generatedAt: '2026-06-09T10:00:00Z',
            coverage: {totalRules: 50, activeRules: 35, deadRules: 15},
            hotRules: [{ruleId: 'r_hot', fireCount: 1000}],
            recentAnomalies: [],
            topRejectReasons: [{reason: 'AGE_TOO_LOW', count: 100}],
            staleDrafts: [
                {draftId: 'drf_old', title: '滞留审批', status: 'PENDING_REVIEW', project: 'demo', daysOld: 7, createdBy: 'BA1'}
            ],
            staleDraftCount: 1,
        });

        render(<RuleHealthView project="demo" />);

        await waitFor(() => {
            // 覆盖率卡片
            expect(screen.getByText('50')).toBeDefined(); // total
            expect(screen.getByText('35')).toBeDefined(); // active
            expect(screen.getByText('15')).toBeDefined(); // dead
        });
        // 滞留草稿标题
        expect(screen.getByText('滞留审批')).toBeDefined();
        // 滞留 count badge
        expect(screen.getByText(/滞留草稿 \(1\)/)).toBeDefined();
        // 热规则
        expect(screen.getByText('r_hot')).toBeDefined();
        // API call
        expect(mockJsonPost).toHaveBeenCalledWith(
            '/agent/tools/get_rule_health',
            expect.objectContaining({project: 'demo', days: 30}),
            expect.objectContaining({silent: true})
        );
    });

    it('should show green "no stale drafts" message when count is 0', async () => {
        mockJsonPost.mockResolvedValueOnce({
            project: 'demo', days: 30, generatedAt: '2026-06-09T10:00:00Z',
            coverage: {totalRules: 10, activeRules: 10, deadRules: 0},
            hotRules: [], recentAnomalies: [], topRejectReasons: [],
            staleDrafts: [], staleDraftCount: 0,
        });

        render(<RuleHealthView project="demo" />);

        await waitFor(() => {
            expect(screen.getByText(/没有滞留草稿/)).toBeDefined();
        });
    });
});
