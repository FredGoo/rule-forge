import {renderReact} from '../../components/react-bridge.js';
import ComparisonOperatorWidget from '../../components/widgets/ComparisonOperatorWidget.jsx';

var OPERATORS = {
    GreaterThen: {label: '大于'},
    GreaterThenEquals: {label: '大于或等于'},
    LessThen: {label: '小于'},
    LessThenEquals: {label: '小于或等于'},
    Equals: {label: '等于'},
    EqualsIgnoreCase: {label: '等于(不分大小写)'},
    StartWith: {label: '开始于'},
    NotStartWith: {label: '不开始于'},
    EndWith: {label: '结束于'},
    NotEndWith: {label: '不结束于'},
    NotEquals: {label: '不等于'},
    NotEqualsIgnoreCase: {label: '不等于(不分大小写)'},
    In: {label: '在集合', endInfo: '之中'},
    NotIn: {label: '不在集合', endInfo: '之中'},
    Null: {label: '为空', noInput: true},
    NotNull: {label: '不为空', noInput: true},
    Match: {label: '匹配正则表达式'},
    NotMatch: {label: '不匹配正则表达式'},
    Contain: {label: '包含'},
    NotContain: {label: '不包含'},
};

ruleforge.ComparisonOperator = function (menuCallFun) {
    this.container = document.createElement("span");
    this.inputType = null;
    this.operatorName = '';
    this.widgetRef = null;
    this._pendingOperator = null;
    var self = this;
    renderReact(ComparisonOperatorWidget, {
        onMenuHide: function () {
            if (menuCallFun) menuCallFun();
        },
        onSelect: function (name) {
            self._applyOperator(name);
        },
        ref: function (ref) {
            self.widgetRef = ref;
            if (self._pendingOperator) {
                ref.setOperator(self._pendingOperator);
                self._pendingOperator = null;
            }
        },
    }, this.container);
};

ruleforge.ComparisonOperator.prototype._applyOperator = function (operator) {
    var config = OPERATORS[operator];
    if (!config) return;
    this.operatorName = operator;
    if (this.inputType) {
        this.inputType.getContainer().remove();
        this.inputType = null;
    }
    if (!config.noInput) {
        this.inputType = new ruleforge.InputType(config.endInfo || null);
    }
    if (this.widgetRef) {
        this.widgetRef.setOperator(operator);
    } else {
        this._pendingOperator = operator;
    }
    window._setDirty();
};

ruleforge.ComparisonOperator.prototype.initRightValue = function (data) {
    if (!this.inputType) {
        return;
    }
    this.inputType.setValueType(data["valueType"], data);
};

ruleforge.ComparisonOperator.prototype.setOperator = function (operator) {
    this._applyOperator(operator);
};

ruleforge.ComparisonOperator.prototype.getOperator = function () {
    if (!this.operatorName) {
        throw "请选择比较操作符！";
    }
    return this.operatorName;
};

ruleforge.ComparisonOperator.prototype.getInputType = function () {
    return this.inputType;
};

ruleforge.ComparisonOperator.prototype.getContainer = function () {
    return this.container;
};
