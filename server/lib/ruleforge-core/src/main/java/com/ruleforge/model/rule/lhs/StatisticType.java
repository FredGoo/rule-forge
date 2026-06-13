package com.ruleforge.model.rule.lhs;
/**
 * @author Jacky.gao
 * @since 2015年5月29日
 */
public enum StatisticType {
	percent,amount,none,
	// V5.50.1:accumulate 5 内置函数 grammar 收口时扩,跟 DRL DrlParser.g4 DRL_COUNT/SUM/
	// AVG/MIN/MAX token 对齐。runtime evaluate 端走 CommonFunctionLeftPart 内部 switch,
	// 老 percent/amount/none 路径不变。
	count,sum,avg,min,max;
}
