/**
 * V5.44.4 — DRL 编辑器 round-trip 客户端。
 *
 * <p>后端 {@code CommonController.loadDrl} 端点响应结构:
 * <pre>{@code
 * {
 *   "path": "/proj/rules/r.drl",
 *   "version": "1.0",          // 可选
 *   "content": "rule \"R1\" ...",
 *   "imports": ["libs/variables.drl", ...],
 *   "ruleNames": ["R1", "R2", ...]
 * }
 * }</pre>
 *
 * <p>本模块只暴露纯函数 {@link loadDrlFile}(fetch + parse),
 * 不绑 React / antd — 组件层自己 wrap。
 *
 * <p>失败语义:
 * <ul>
 *   <li>文件不存在 → 404 → throw Error("file not found: ...")</li>
 *   <li>DRL 语法错 → 200 + 空 imports/ruleNames(后端 lenient parser 设计)</li>
 *   <li>网络错 → throw(由 caller 处理)</li>
 * </ul>
 *
 * @since 5.44
 */

import {formPost} from './client';

export interface DrlFilePayload {
    /** 项目内文件路径(/project/...) */
    path: string;
    /** 文件版本号,可选 */
    version?: string;
    /** 完整 DRL 文本 — 编辑器主区 */
    content: string;
    /** 顶层 import 段解析结果(按文件出现顺序,去重) */
    imports: string[];
    /** DRL 中所有 rule 名称(语法错时为空 list) */
    ruleNames: string[];
}

/**
 * V5.44.4 — 加载 DRL 文件。轻量 fetch,后端走 lenient parser,
 * 语法错 DRL 也返 200(空 imports/ruleNames),由调用方决定如何展示。
 *
 * @param file  — 文件路径(需 URL-encoded 吗? 后端会自己 decode)
 * @param version — 可选版本号
 * @returns DrlFilePayload
 * @throws Error 文件不存在(404)或网络错
 */
export function loadDrlFile(file: string, version?: string): Promise<DrlFilePayload> {
    const params: Record<string, string> = {file};
    if (version) params.version = version;
    // V5.44.4 决定:走 /loadDrl 端点(后端 V5.44.4 加),不复用 /loadXml
    return formPost<DrlFilePayload>('/common/loadDrl', params, {silent: true});
}
