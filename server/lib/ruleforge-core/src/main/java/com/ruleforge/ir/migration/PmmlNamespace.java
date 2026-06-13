package com.ruleforge.ir.migration;

/**
 * V5.41.5 — PMML 4.4 namespace 常量(给一次性 .xml → .pmml 迁移工具用)。
 *
 * <p>基于 pmml4s 1.5.6 实测确认:pmml4s 同时认 PMML 4.4 ({@code http://www.dmg.org/PMML-4_4})
 * 和 PMML 4.3 ({@code http://www.dmg.org/PMML-4_3})。本迁移工具只产 4.4(跟 plan 锁定一致)。
 *
 * @since 5.41
 */
public final class PmmlNamespace {
    private PmmlNamespace() {
        // utility class
    }

    public static final String PMML_4_4 = "http://www.dmg.org/PMML-4_4";
}
