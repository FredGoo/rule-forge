package com.ruleforge.ir.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.42.3a — Drools 6 .dsl mapping 集合。
 *
 * <p>内部按 scope 分桶(3 个 ArrayList),保证:
 * <ul>
 *   <li>同 scope 内顺序保留(.dsl 文件声明顺序 → 用于 Drools 6 兼容的 longest-match 优先级)</li>
 *   <li>{@link #lookup} 时,先查精确 scope,再回退到 {@link DslScope#ANY}</li>
 * </ul>
 *
 * @since 5.42
 */
public class DslMappingSet {

    private final List<DslEntry> whenEntries = new ArrayList<>();
    private final List<DslEntry> thenEntries = new ArrayList<>();
    private final List<DslEntry> anyEntries = new ArrayList<>();

    /** 给 DslParser 调。 */
    public void add(DslEntry entry) {
        switch (entry.getScope()) {
            case WHEN: whenEntries.add(entry); break;
            case THEN: thenEntries.add(entry); break;
            case ANY:  anyEntries.add(entry); break;
        }
    }

    public int size() {
        return whenEntries.size() + thenEntries.size() + anyEntries.size();
    }

    /**
     * 给 (scope, key) 找 entry。语义:
     * <ol>
     *   <li>先查精确 scope 桶(when→when / then→then),key 全文匹配</li>
     *   <li>未命中 → 查 {@link DslScope#ANY} 桶</li>
     *   <li>仍未命中 → null</li>
     * </ol>
     *
     * <p>同 scope 内有多个 key 匹配时,V5.42.3a 选**第一个**(Drools 6 兼容的
     * 声明顺序优先)。caller 想 longest-match 自己排序后调 lookup。
     */
    public DslEntry lookup(DslScope scope, String key) {
        List<DslEntry> primary = (scope == DslScope.WHEN) ? whenEntries : thenEntries;
        for (DslEntry e : primary) {
            if (e.getKey().equals(key)) {
                return e;
            }
        }
        for (DslEntry e : anyEntries) {
            if (e.getKey().equals(key)) {
                return e;
            }
        }
        return null;
    }

    // debug-only
    public List<DslEntry> getAllEntries() {
        List<DslEntry> all = new ArrayList<>();
        all.addAll(whenEntries);
        all.addAll(thenEntries);
        all.addAll(anyEntries);
        return all;
    }
}
