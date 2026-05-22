package com.ruleforge.console.app.service;

import com.ruleforge.console.app.dto.LoanEvaluateRequest;
import com.ruleforge.console.app.dto.LoanEvaluateResponse;

/**
 * 贷款决策评估服务接口
 */
public interface ILoanDecisionService {

    /**
     * 执行贷款决策评估
     *
     * @param request 评估请求
     * @return 评估响应
     */
    LoanEvaluateResponse evaluate(LoanEvaluateRequest request);
}
