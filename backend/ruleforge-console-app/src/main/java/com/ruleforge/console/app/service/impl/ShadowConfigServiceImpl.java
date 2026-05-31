package com.ruleforge.console.app.service.impl;

import com.ruleforge.console.app.entity.ShadowConfig;
import com.ruleforge.console.app.repository.data.DatasourceRepository;
import com.ruleforge.console.app.service.IShadowConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 陪跑配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowConfigServiceImpl implements IShadowConfigService {

    private final DatasourceRepository datasourceRepository;

    @Override
    public List<ShadowConfig> findEnabledByMainPath(String mainRulePackagePath) {
        return datasourceRepository.findEnabledShadowConfigsByMainPath(mainRulePackagePath);
    }

    @Override
    public boolean shouldExecuteShadow(ShadowConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        Integer sampleRate = config.getSampleRate();
        if (sampleRate == null || sampleRate <= 0) {
            return false;
        }
        if (sampleRate >= 100) {
            return true;
        }
        // 随机采样
        int random = ThreadLocalRandom.current().nextInt(100);
        return random < sampleRate;
    }
}
