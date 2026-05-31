package com.ruleforge.decision.service;

import com.ruleforge.decision.entity.ShadowConfig;

import java.util.List;

/**
 * 陪跑配置服务接口
 */
public interface IShadowConfigService {

    /**
     * 根据主规则包路径查询启用的陪跑配置
     */
    List<ShadowConfig> findEnabledByMainPath(String mainRulePackagePath);

    /**
     * 判断是否应该执行陪跑（根据采样率）
     */
    boolean shouldExecuteShadow(ShadowConfig config);
}
