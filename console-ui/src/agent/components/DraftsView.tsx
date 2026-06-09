import {Component} from 'react';
import {jsonPost} from '@/api/client';

// ====== 类型定义 ======

export interface DraftDto {
    draftId: string;
    ruleType: string;
    project: string;
    packagePath: string | null;
    status: 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'EXPIRED';
    title: string | null;
    source: string;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
    reviewedBy: string | null;
    reviewedAt: string | null;
    reviewComment: string | null;
    appliedVersion: string | null;
    appliedAt: string | null;
    expiresAt: string | null;
    content: any;
}

interface DraftsViewProps {
    project?: string;
    username?: string;
    onApply?: (draftId: string) => void;
}

interface DraftsViewState {
    drafts: DraftDto[];
    loading: boolean;
    filterStatus: string;
    selected: DraftDto | null;
    detail: DraftDto | null;
    detailLoading: boolean;
    actionLoading: boolean;
    errorMsg: string | null;
}

// ====== 工具函数 ======

const STATUS_COLORS: Record<string, {bg: string; fg: string; label: string}> = {
    DRAFT:          {bg: '#f0f0f0', fg: '#666', label: '草稿'},
    PENDING_REVIEW: {bg: '#fff7e6', fg: '#d46b08', label: '待审批'},
    APPROVED:       {bg: '#f6ffed', fg: '#389e0d', label: '已通过'},
    REJECTED:       {bg: '#fff1f0', fg: '#cf1322', label: '已拒绝'},
    EXPIRED:        {bg: '#f5f5f5', fg: '#999', label: '已过期'},
};

const STATUS_KEYS = ['', 'DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED'];

/**
 * V5.22 — 草稿列表 / 审批 / 应用 UI
 *
 * 路径:BA 在 AI 助手面板 → "草稿" tab
 * 操作:
 *   - 列表(可按 status 过滤)
 *   - 详情(content JSON 展示)
 *   - 提交审批(DRAFT → PENDING_REVIEW)
 *   - 审批通过 / 拒绝(PENDING_REVIEW → APPROVED / REJECTED)
 *   - 应用到包(APPROVED → 写主存储)
 *   - 重新生成测试用例 + 跑测试
 */
export default class DraftsView extends Component<DraftsViewProps, DraftsViewState> {

    state: DraftsViewState = {
        drafts: [],
        loading: false,
        filterStatus: '',
        selected: null,
        detail: null,
        detailLoading: false,
        actionLoading: false,
        errorMsg: null,
    };

    componentDidMount() {
        this.loadDrafts();
    }

    componentDidUpdate(prev: DraftsViewProps) {
        if (prev.project !== this.props.project) {
            this.loadDrafts();
        }
    }

    loadDrafts = async () => {
        this.setState({loading: true, errorMsg: null});
        try {
            const body: Record<string, any> = {limit: 50};
            if (this.props.project) body.project = this.props.project;
            if (this.state.filterStatus) body.status = this.state.filterStatus;
            const resp = await jsonPost<{drafts: DraftDto[]; count: number}>('/agent/tools/list_drafts', body, {silent: true});
            this.setState({drafts: Array.isArray(resp?.drafts) ? resp.drafts : [], loading: false});
        } catch (e) {
            this.setState({errorMsg: '加载草稿失败: ' + (e as Error).message, loading: false});
        }
    };

    loadDetail = async (draftId: string) => {
        this.setState({detailLoading: true});
        try {
            const detail = await jsonPost<DraftDto>('/agent/tools/get_draft', {draftId}, {silent: true});
            this.setState({detail, detailLoading: false});
        } catch (e) {
            this.setState({detailLoading: false, errorMsg: '加载详情失败'});
        }
    };

    handleAction = async (action: 'submit' | 'approve' | 'reject' | 'apply', draftId: string, extra?: any) => {
        const username = this.props.username || 'BA';
        this.setState({actionLoading: true, errorMsg: null});
        try {
            const toolName = action === 'submit' ? 'submit_draft'
                : action === 'approve' ? 'approve_draft'
                : action === 'reject' ? 'reject_draft'
                : 'apply_draft';
            const body: any = {draftId};
            if (action === 'submit') body.submittedBy = username;
            else if (action === 'approve') { body.reviewer = username; body.comment = extra || ''; }
            else if (action === 'reject') { body.reviewer = username; body.reason = extra || ''; }
            else if (action === 'apply') {
                body.packagePath = extra?.packagePath;
                body.fileName = extra?.fileName;
                body.reviewer = username;
                body.versionComment = extra?.versionComment || 'V5.22 AI 草稿应用';
            }
            await jsonPost(`/agent/tools/${toolName}`, body, {errorPrefix: `操作失败 (${action})`});
            await this.loadDrafts();
            if (this.state.selected?.draftId === draftId) {
                await this.loadDetail(draftId);
            }
            if (action === 'apply' && this.props.onApply) {
                this.props.onApply(draftId);
            }
        } catch (e) {
            this.setState({errorMsg: `操作失败: ${(e as Error).message}`});
        } finally {
            this.setState({actionLoading: false});
        }
    };

    handleApplyClick = (draft: DraftDto) => {
        const packagePath = prompt('目标包路径(packagePath):', draft.packagePath || '/demo');
        if (!packagePath) return;
        const fileName = prompt('文件名(留空自动):', '');
        const versionComment = prompt('版本说明:', 'V5.22 AI 草稿应用 — ' + (draft.title || draft.draftId));
        if (versionComment === null) return;
        this.handleAction('apply', draft.draftId, {packagePath, fileName, versionComment});
    };

    handleRejectClick = (draft: DraftDto) => {
        const reason = prompt('拒绝原因:');
        if (!reason) return;
        this.handleAction('reject', draft.draftId, reason);
    };

    handleApproveClick = (draft: DraftDto) => {
        const comment = prompt('审批意见(可空):', '');
        if (comment === null) return;
        this.handleAction('approve', draft.draftId, comment);
    };

    handleTestRun = async (draft: DraftDto) => {
        this.setState({actionLoading: true, errorMsg: null});
        try {
            // 1. 生成测试用例
            const gen = await jsonPost<{testCases: any[]; count: number}>(
                '/agent/tools/generate_test_cases', {draftId: draft.draftId, count: 5}, {silent: true}
            );
            // 2. 跑测试
            const run = await jsonPost<{passed: number; failed: number; results: any[]}>(
                '/agent/tools/run_test', {draftId: draft.draftId, testCases: gen.testCases}, {silent: true}
            );
            alert(`测试结果: ${run.passed} PASS / ${run.failed} FAIL\n\n` +
                run.results.map((r: any) =>
                    `  ${r.status === 'PASS' ? '✅' : '❌'} ${r.name} → ${r.matchedRowId || 'no match'}`
                ).join('\n'));
        } catch (e) {
            this.setState({errorMsg: '测试运行失败: ' + (e as Error).message});
        } finally {
            this.setState({actionLoading: false});
        }
    };

    renderStatusBadge(status: string) {
        const s = STATUS_COLORS[status] || STATUS_COLORS.DRAFT;
        return (
            <span style={{
                padding: '2px 8px',
                borderRadius: 4,
                fontSize: 11,
                fontWeight: 500,
                background: s.bg,
                color: s.fg,
            }}>{s.label}</span>
        );
    }

    renderToolbar() {
        return (
            <div style={{display: 'flex', gap: 4, padding: '8px 12px', borderBottom: '1px solid #e8e8e8'}}>
                <select
                    value={this.state.filterStatus}
                    onChange={e => { this.setState({filterStatus: e.target.value}, () => this.loadDrafts()); }}
                    style={{padding: '4px 8px', fontSize: 12, borderRadius: 4, border: '1px solid #d9d9d9'}}
                >
                    {STATUS_KEYS.map(k => (
                        <option key={k} value={k}>
                            {k === '' ? '全部状态' : (STATUS_COLORS[k]?.label || k)}
                        </option>
                    ))}
                </select>
                <button
                    className="btn btn-xs btn-default"
                    onClick={this.loadDrafts}
                    disabled={this.state.loading}
                    style={{marginLeft: 'auto'}}
                >
                    <i className="glyphicon glyphicon-refresh" /> 刷新
                </button>
            </div>
        );
    }

    renderDetail(detail: DraftDto) {
        const contentJson = typeof detail.content === 'string'
            ? detail.content
            : JSON.stringify(detail.content, null, 2);
        return (
            <div style={{flex: 1, overflow: 'auto', padding: 12, background: '#fafafa'}}>
                <div style={{marginBottom: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                    <div>
                        <div style={{fontSize: 14, fontWeight: 600, marginBottom: 4}}>
                            {detail.title || detail.draftId}
                        </div>
                        <div style={{fontSize: 11, color: '#999'}}>
                            {detail.ruleType} · {detail.project} · {detail.createdBy} ·{' '}
                            {detail.createdAt?.substring(0, 16).replace('T', ' ')}
                        </div>
                    </div>
                    <button className="btn btn-xs btn-default" onClick={() => this.setState({detail: null})}>
                        <i className="glyphicon glyphicon-arrow-left" /> 返回列表
                    </button>
                </div>

                {/* 元信息 */}
                <div style={{display: 'grid', gridTemplateColumns: '100px 1fr', gap: '4px 12px', fontSize: 12, marginBottom: 12}}>
                    <span style={{color: '#999'}}>状态:</span>
                    <span>{this.renderStatusBadge(detail.status)}</span>
                    <span style={{color: '#999'}}>草稿 ID:</span>
                    <code style={{fontSize: 11}}>{detail.draftId}</code>
                    {detail.reviewedBy && (<>
                        <span style={{color: '#999'}}>{detail.status === 'REJECTED' ? '拒绝人' : '审批人'}:</span>
                        <span>{detail.reviewedBy} ({detail.reviewedAt?.substring(0, 16).replace('T', ' ')})</span>
                    </>)}
                    {detail.reviewComment && (<>
                        <span style={{color: '#999'}}>意见:</span>
                        <span>{detail.reviewComment}</span>
                    </>)}
                    {detail.appliedVersion && (<>
                        <span style={{color: '#999'}}>应用版本:</span>
                        <span>{detail.appliedVersion}</span>
                    </>)}
                    {detail.expiresAt && (<>
                        <span style={{color: '#999'}}>过期时间:</span>
                        <span>{detail.expiresAt.substring(0, 16).replace('T', ' ')}</span>
                    </>)}
                </div>

                {/* 操作按钮 */}
                <div style={{display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap'}}>
                    {detail.status === 'DRAFT' && (
                        <button className="btn btn-xs btn-primary" disabled={this.state.actionLoading}
                                onClick={() => this.handleAction('submit', detail.draftId)}>
                            <i className="glyphicon glyphicon-send" /> 提交审批
                        </button>
                    )}
                    {detail.status === 'PENDING_REVIEW' && (<>
                        <button className="btn btn-xs btn-success" disabled={this.state.actionLoading}
                                onClick={() => this.handleApproveClick(detail)}>
                            <i className="glyphicon glyphicon-ok" /> 审批通过
                        </button>
                        <button className="btn btn-xs btn-danger" disabled={this.state.actionLoading}
                                onClick={() => this.handleRejectClick(detail)}>
                            <i className="glyphicon glyphicon-remove" /> 拒绝
                        </button>
                    </>)}
                    {detail.status === 'APPROVED' && (
                        <button className="btn btn-xs btn-primary" disabled={this.state.actionLoading}
                                onClick={() => this.handleApplyClick(detail)}>
                            <i className="glyphicon glyphicon-save" /> 应用到包
                        </button>
                    )}
                    <button className="btn btn-xs btn-default" disabled={this.state.actionLoading}
                            onClick={() => this.handleTestRun(detail)}>
                        <i className="glyphicon glyphicon-play" /> 生成 + 跑测试
                    </button>
                </div>

                {/* Content */}
                <div style={{fontSize: 12, fontWeight: 600, color: '#666', marginBottom: 4}}>规则内容 (JSON):</div>
                <pre style={{
                    background: '#fff',
                    border: '1px solid #e8e8e8',
                    borderRadius: 4,
                    padding: 12,
                    fontSize: 11,
                    lineHeight: 1.5,
                    maxHeight: 400,
                    overflow: 'auto',
                    margin: 0,
                }}>
                    {contentJson}
                </pre>
            </div>
        );
    }

    render() {
        const {drafts, loading, detail, detailLoading, errorMsg, filterStatus} = this.state;

        if (detail) {
            return this.renderDetail(detail);
        }

        return (
            <div style={{display: 'flex', flexDirection: 'column', height: '100%'}}>
                {this.renderToolbar()}

                {errorMsg && (
                    <div style={{padding: '8px 12px', background: '#fff1f0', color: '#cf1322', fontSize: 12}}>
                        {errorMsg}
                    </div>
                )}

                <div style={{flex: 1, overflow: 'auto'}}>
                    {loading && (
                        <div style={{padding: 20, textAlign: 'center', color: '#999', fontSize: 12}}>
                            <i className="glyphicon glyphicon-refresh" /> 加载中...
                        </div>
                    )}
                    {!loading && drafts.length === 0 && (
                        <div style={{padding: 40, textAlign: 'center', color: '#999', fontSize: 12}}>
                            <i className="glyphicon glyphicon-inbox" style={{fontSize: 32, display: 'block', marginBottom: 8}}/>
                            {filterStatus ? `没有 ${STATUS_COLORS[filterStatus]?.label || filterStatus} 状态的草稿` : '该项目暂无草稿'}
                            <div style={{marginTop: 12, fontSize: 11}}>
                                在 AI 助手对话里说 "写一个 18 岁以下拒贷的规则",LLM 会自动创建草稿
                            </div>
                        </div>
                    )}
                    {!loading && drafts.length > 0 && drafts.map(d => (
                        <div
                            key={d.draftId}
                            onClick={() => this.loadDetail(d.draftId)}
                            style={{
                                padding: '10px 12px',
                                borderBottom: '1px solid #f0f0f0',
                                cursor: 'pointer',
                            }}
                            onMouseEnter={e => (e.currentTarget.style.background = '#f5f5f5')}
                            onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
                        >
                            <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4}}>
                                <span style={{fontSize: 13, fontWeight: 500, color: '#333'}}>
                                    {d.title || d.draftId}
                                </span>
                                {this.renderStatusBadge(d.status)}
                            </div>
                            <div style={{fontSize: 11, color: '#999'}}>
                                {d.ruleType} · {d.createdBy} · {d.createdAt?.substring(0, 16).replace('T', ' ')}
                            </div>
                            {d.reviewComment && (
                                <div style={{
                                    fontSize: 11,
                                    color: '#666',
                                    marginTop: 4,
                                    fontStyle: 'italic',
                                }}>
                                    💬 {d.reviewComment.substring(0, 80)}
                                </div>
                            )}
                        </div>
                    ))}
                </div>

                {detailLoading && (
                    <div style={{position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(255,255,255,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center'}}>
                        <i className="glyphicon glyphicon-refresh" />
                    </div>
                )}
            </div>
        );
    }
}
