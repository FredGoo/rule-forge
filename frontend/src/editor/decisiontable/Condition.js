ruleforge.Condition = function (parentContainer) {
    this.container = document.createElement("span");
    parentContainer.append(this.container);
    var self = this;
    this.operator = new ruleforge.ComparisonOperator(function () {
        self.inputType = self.operator.getInputType();
        if (self.inputType) {
            self.container.appendChild(self.inputType.getContainer());
        }
    });
    self.container.appendChild(this.operator.getContainer());
};
ruleforge.Condition.prototype.initData = function (data) {
    var op = data["op"];
    this.operator.setOperator(op);
    this.operator.initRightValue(data["value"]);
    this.inputType = this.operator.getInputType();
    if (this.inputType) {
        this.container.appendChild(this.inputType.getContainer());
    }
};
ruleforge.Condition.prototype.getDisplayContainer = function () {
    var container = document.createElement("span");
    var operator = this.operator.getContainer().textContent;
    var opSpan = document.createElement("span");
    opSpan.style.cssText = "color:blue";
    opSpan.textContent = operator;
    container.appendChild(opSpan);
    if (this.inputType) {
        container.appendChild(this.inputType.getDisplayContainer());
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
