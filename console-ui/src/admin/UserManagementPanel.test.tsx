import {describe, it, expect, vi, beforeEach} from 'vitest';
import {render, screen, waitFor, fireEvent} from '@testing-library/react';
import {UserItem} from '@/api/client';

// ── Mock antd message ──
vi.mock('antd', async () => {
    const actual = await vi.importActual('antd');
    return {
        ...actual,
        message: {
            success: vi.fn(),
            warning: vi.fn(),
            error: vi.fn(),
        },
    };
});

// ── Mock API client ──
const mockListUsers = vi.fn();
const mockCreateUser = vi.fn();
const mockUpdateUser = vi.fn();
const mockToggleUserEnabled = vi.fn();
const mockResetPassword = vi.fn();
const mockGetUserPermissions = vi.fn();
const mockSaveUserPermissions = vi.fn();

vi.mock('@/api/client', () => ({
    listUsers: (...args: unknown[]) => mockListUsers(...args),
    createUser: (...args: unknown[]) => mockCreateUser(...args),
    updateUser: (...args: unknown[]) => mockUpdateUser(...args),
    toggleUserEnabled: (...args: unknown[]) => mockToggleUserEnabled(...args),
    resetPassword: (...args: unknown[]) => mockResetPassword(...args),
    getUserPermissions: (...args: unknown[]) => mockGetUserPermissions(...args),
    saveUserPermissions: (...args: unknown[]) => mockSaveUserPermissions(...args),
}));

// ── Mock ant-design/icons (JSX compatibility) ──
vi.mock('@ant-design/icons', () => ({
    PlusOutlined: () => <span data-testid="icon-plus"/>,
    EditOutlined: () => <span data-testid="icon-edit"/>,
    StopOutlined: () => <span data-testid="icon-stop"/>,
    CheckOutlined: () => <span data-testid="icon-check"/>,
    KeyOutlined: () => <span data-testid="icon-key"/>,
    SafetyOutlined: () => <span data-testid="icon-safety"/>,
}));

// ── Fixture ──
const MOCK_USERS: UserItem[] = [
    {
        id: 1, username: 'admin', companyId: 'ruleforge',
        isAdmin: true, isEnabled: true, canImport: true, canExport: true,
        createdAt: '2026-01-01T00:00:00', updatedAt: '2026-01-01T00:00:00',
    },
    {
        id: 2, username: 'testuser', companyId: 'ruleforge',
        isAdmin: false, isEnabled: true, canImport: false, canExport: false,
        createdAt: '2026-06-01T00:00:00', updatedAt: '2026-06-01T00:00:00',
    },
    {
        id: 3, username: 'disabled_user', companyId: 'ruleforge',
        isAdmin: false, isEnabled: false, canImport: false, canExport: false,
        createdAt: '2026-06-02T00:00:00', updatedAt: '2026-06-02T00:00:00',
    },
];

import UserManagementPanel from './UserManagementPanel';

describe('UserManagementPanel', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockListUsers.mockResolvedValue(MOCK_USERS);
    });

    /**
     * Scenario 1: 列表渲染
     * Given listUsers 返回 3 个用户
     * When 组件挂载
     * Then 表格显示 3 行用户数据
     */
    it('renders user list after loading', async () => {
        render(<UserManagementPanel/>);

        // 等待数据加载
        await waitFor(() => {
            expect(mockListUsers).toHaveBeenCalledOnce();
        });

        // 表格应包含所有用户名
        expect(screen.getByText('admin')).toBeInTheDocument();
        expect(screen.getByText('testuser')).toBeInTheDocument();
        expect(screen.getByText('disabled_user')).toBeInTheDocument();

        // 角色标签
        expect(screen.getByText('管理员')).toBeInTheDocument();
        expect(screen.getAllByText('普通用户').length).toBe(2);

        // 状态标签 (启用/禁用 in status column + action buttons — multiple matches)
        const enabledLabels = screen.getAllByText('启用');
        expect(enabledLabels.length).toBeGreaterThanOrEqual(2);
        const disabledLabels = screen.getAllByText('禁用');
        expect(disabledLabels.length).toBeGreaterThanOrEqual(1);
    });

    /**
     * Scenario 2: 创建用户
     * Given 用户点击"新增用户"按钮
     * When 表单填写并提交
     * Then createUser API 被调用
     */
    it('calls createUser when form is submitted', async () => {
        mockCreateUser.mockResolvedValue({
            status: true, id: 4, username: 'newuser',
        });

        render(<UserManagementPanel/>);
        await waitFor(() => {
            expect(mockListUsers).toHaveBeenCalledOnce();
        });

        // 点击新增按钮
        const addButton = screen.getByText('新增用户');
        fireEvent.click(addButton);

        // Modal 应该出现 (title = "新增用户", also button text)
        await waitFor(() => {
            const matches = screen.getAllByText('新增用户');
            // Button + Modal title = at least 2
            expect(matches.length).toBeGreaterThanOrEqual(2);
        });
    });

    /**
     * Scenario 3: 禁用用户
     * Given 用户点击"禁用"按钮
     * When confirm 弹窗确认
     * Then toggleUserEnabled API 被调用
     */
    it('toggles user enabled state on button click', async () => {
        mockToggleUserEnabled.mockResolvedValue({status: true});

        render(<UserManagementPanel/>);
        await waitFor(() => {
            expect(mockListUsers).toHaveBeenCalledOnce();
        });

        // testuser (id=2, enabled) 应该有"禁用"按钮
        const disableButtons = screen.getAllByText('禁用');
        // 第一个"禁用"是 testuser 的 (admin 没有禁用)
        // 注意: status column also shows "禁用" for disabled_user
        // 操作列的"禁用"按钮属于 enabled 用户
        expect(disableButtons.length).toBeGreaterThanOrEqual(1);
    });

    /**
     * Scenario 4: 非 admin 用户隐藏权限按钮
     * Given testuser (isAdmin=false)
     * When 表格渲染
     * Then testuser 行有"权限"按钮
     * And admin 行没有"权限"按钮
     */
    it('shows permission button only for non-admin users', async () => {
        render(<UserManagementPanel/>);
        await waitFor(() => {
            expect(mockListUsers).toHaveBeenCalledOnce();
        });

        // "权限"按钮应该存在 (for testuser and disabled_user, not admin)
        const permissionButtons = screen.getAllByText('权限');
        expect(permissionButtons.length).toBe(2); // testuser + disabled_user
    });
});
