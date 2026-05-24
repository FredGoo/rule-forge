ruleforge.ComparisonOperator = function (menuCallFun) {
    this.inputType = null;
    this.operator = "";
    this.container = generateContainer();
    RuleForge.setDomContent(this.container, "请选择比较操作符");
    this.container.style.cssText += ";font-size:13px;color:red;fontWeight:bold;margin-right:3px;";
    var self = this;
    var onClick = function (menu) {
        self.setOperator(menu.name);
    };
    self.menu = new RuleForge.menu.Menu({
        onHide: function () {
            menuCallFun();
        },
        menuItems: [{
            label: "大于",
            name: "GreaterThen",
            onClick: onClick
        }, {
            label: "大于或等于",
            name: "GreaterThenEquals",
            onClick: onClick
        }, {
            label: "小于",
            name: "LessThen",
            onClick: onClick
        }, {
            label: "小于或等于",
            name: "LessThenEquals",
            onClick: onClick
        }, {
            label: "等于",
            name: "Equals",
            onClick: onClick
        }, {
            label: "等于(不分大小写)",
            name: "EqualsIgnoreCase",
            onClick: onClick
        }, {
            label: "开始于",
            name: "StartWith",
            onClick: onClick
        }, {
            label: "不开始于",
            name: "NotStartWith",
            onClick: onClick
        }, {
            label: "结束于",
            name: "EndWith",
            onClick: onClick
        }, {
            label: "不结束于",
            name: "NotEndWith",
            onClick: onClick
        }, {
            label: "不等于",
            name: "NotEquals",
            onClick: onClick
        }, {
            label: "不等于(不分大小写)",
            name: "NotEqualsIgnoreCase",
            onClick: onClick
        }, {
            label: "在集合",
            name: "In",
            onClick: onClick
        }, {
            label: "不在集合",
            name: "NotIn",
            onClick: onClick
        }, {
            label: "为空",
            name: "Null",
            onClick: onClick
        }, {
            label: "不为空",
            name: "NotNull",
            onClick: onClick
        }, {
            label: "匹配正则表达式",
            name: "Match",
            onClick: onClick
        }, {
            label: "不匹配正则表达式",
            name: "NotMatch",
            onClick: onClick
        }, {
            label: "包含",
            name: "Contain",
            onClick: onClick
        }, {
            label: "不包含",
            name: "NotContain",
            onClick: onClick
        }]
    });
    this.container.addEventListener("click", function (e) {
        self.menu.show(e);
    });

};

ruleforge.ComparisonOperator.prototype.initRightValue = function (data) {
    if (!this.inputType) {
        return;
    }
    this.inputType.setValueType(data["valueType"], data);
};

ruleforge.ComparisonOperator.prototype.setOperator = function (operator) {
    switch (operator) {
        case "GreaterThen":
            this.operator = "GreaterThen";
            RuleForge.setDomContent(this.container, "大于");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "GreaterThenEquals":
            this.operator = "GreaterThenEquals";
            RuleForge.setDomContent(this.container, "大于或等于");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "LessThen":
            this.operator = "LessThen";
            RuleForge.setDomContent(this.container, "小于");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "LessThenEquals":
            this.operator = "LessThenEquals";
            RuleForge.setDomContent(this.container, "小于或等于");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "StartWith":
            this.operator = "StartWith";
            RuleForge.setDomContent(this.container, "开始于");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "Equals":
            this.operator = "Equals";
            RuleForge.setDomContent(this.container, "等于");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "EqualsIgnoreCase":
            this.operator = "EqualsIgnoreCase";
            RuleForge.setDomContent(this.container, "等于(不分大小写)");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "NotStartWith":
            this.operator = "NotStartWith";
            RuleForge.setDomContent(this.container, "不开始于");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "EndWith":
            this.operator = "EndWith";
            RuleForge.setDomContent(this.container, "结束于");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "NotEndWith":
            this.operator = "NotEndWith";
            RuleForge.setDomContent(this.container, "不结束于");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "NotEquals":
            this.operator = "NotEquals";
            RuleForge.setDomContent(this.container, "不等于");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "NotEqualsIgnoreCase":
            this.operator = "NotEqualsIgnoreCase";
            RuleForge.setDomContent(this.container, "不等于(不分大小写)");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "In":
            this.operator = "In";
            RuleForge.setDomContent(this.container, "在集合");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType("之中");
            break;
        case "NotIn":
            this.operator = "NotIn";
            RuleForge.setDomContent(this.container, "不在集合");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType("之中");
            break;
        case "Null":
            this.operator = "Null";
            RuleForge.setDomContent(this.container, "为空");
            if (this.inputType) {
                this.inputType.getContainer().remove();
                this.inputType = null;
            }
            break;
        case "NotNull":
            this.operator = "NotNull";
            RuleForge.setDomContent(this.container, "不为空");
            if (this.inputType) {
                this.inputType.getContainer().remove();
                this.inputType = null;
            }
            break;
        case "Match":
            this.operator = "Match";
            RuleForge.setDomContent(this.container, "匹配正则表达式");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "NotMatch":
            this.operator = "NotMatch";
            RuleForge.setDomContent(this.container, "不匹配正则表达式");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "Contain":
            this.operator = "Contain";
            RuleForge.setDomContent(this.container, "包含");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
        case "NotContain":
            this.operator = "NotContain";
            RuleForge.setDomContent(this.container, "不包含");
            if (this.inputType) {
                this.inputType.getContainer().remove();
            }
            this.inputType = new ruleforge.InputType();
            break;
    }
    window._setDirty();
};

ruleforge.ComparisonOperator.prototype.getOperator = function () {
    if (this.operator == "") {
        throw "请选择比较操作符！";
    }
    return this.operator;
};

ruleforge.ComparisonOperator.prototype.getInputType = function () {
    return this.inputType;
};

ruleforge.ComparisonOperator.prototype.getContainer = function () {
    return this.container;
};