package com.ruleforge.console.app.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 贷款决策评估响应
 */
@Data
public class LoanEvaluateResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 决策流执行后的所有参数数据
     * 包含决策流输出的所有字段
     */
    private Map<String, Object> data;

    /**
     * 错误信息（当success=false时）
     */
    private String message;

    /**
     * 成功响应构造器
     */
    public static LoanEvaluateResponse success(Map<String, Object> data) {
        LoanEvaluateResponse response = new LoanEvaluateResponse();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    /**
     * 失败响应构造器
     */
    public static LoanEvaluateResponse failure(String message) {
        LoanEvaluateResponse response = new LoanEvaluateResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    /**
     * 异步等待响应构造器
     * 表示决策暂停，等待异步数据源返回
     */
    public static LoanEvaluateResponse asyncPending(String asyncDataSourceId, boolean taskTriggered) {
        Map<String, Object> data = new HashMap<>();
        data.put("asyncPending", true);
        data.put("asyncDataSourceId", asyncDataSourceId);
        data.put("asyncTaskTriggered", taskTriggered);

        LoanEvaluateResponse response = new LoanEvaluateResponse();
        response.setSuccess(true);  // 暂停不是失败
        response.setData(data);
        return response;
    }
}
