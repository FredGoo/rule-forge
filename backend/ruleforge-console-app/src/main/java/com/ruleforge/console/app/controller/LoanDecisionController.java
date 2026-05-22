package com.ruleforge.console.app.controller;

import com.ruleforge.console.app.dto.LoanEvaluateRequest;
import com.ruleforge.console.app.dto.LoanEvaluateResponse;
import com.ruleforge.console.app.service.ILoanDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 贷款决策评估 Controller
 * 用于生产环境接收贷款申请系统的决策流调用
 */
@Slf4j
@RestController
@RequestMapping("/api/loan")
@RequiredArgsConstructor
public class LoanDecisionController {

    private final ILoanDecisionService loanDecisionService;

    /**
     * 贷款决策评估接口
     *
     * @param request 评估请求，包含 userId, rulePackagePath, flowId
     * @return 决策流执行后的所有参数
     */
    @PostMapping("/evaluate")
    public LoanEvaluateResponse evaluate(@Valid @RequestBody LoanEvaluateRequest request) {
        return loanDecisionService.evaluate(request);
    }
}
