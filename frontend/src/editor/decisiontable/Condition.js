/**
 * @author GJ
 */
ruleforge.Condition = function (parentContainer) {
    this.container = $("<span>");
    parentContainer.append(this.container);
    var self = this;
    this.operator = new ruleforge.ComparisonOperator(function () {
        self.inputType = self.operator.getInputType();
        if (self.inputType) {
            self.container.append(self.inputType.getContainer());
        }
    });
    self.container.append(this.operator.getContainer());
};
ruleforge.Condition.prototype.initData = function (data) {
    var op = data["op"];
    this.operator.setOperator(op);
    this.operator.initRightValue(data["value"]);
    this.inputType = this.operator.getInputType();
    if (this.inputType) {
        this.container.append(this.inputType.getContainer());
    }
};
ruleforge.Condition.prototype.getDisplayContainer = function () {
    var container = $("<span>");
    var operator = RuleForge.getDomContent(this.operator.getContainer());
    container.append($("<span style='color:blue'>" + operator + "</span>"));
    if (this.inputType) {
        container.append(this.inputType.getDisplayContainer());
    }
    return container;
};
ruleforge.Condition.prototype.toXml = function () {
    var xml = "<condition op=\"" + this.operator.getOperator() + "\">";
    if (this.inputType) {
        xml += this.inputType.toXml();
    }
    xml += "</condition>";
    return xml;
};
ruleforge.Condition.prototype.getOperator = function () {
    return this.operator;
};
ruleforge.Condition.prototype.getInputType = function () {
    return this.inputType;
};