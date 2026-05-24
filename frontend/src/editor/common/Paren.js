ruleforge.Paren = function (rule) {
    this.container = document.createElement("span");
    this.leftParen = document.createElement("span");
    this.leftParen.textContent = "(";
    this.leftParen.style.cssText = "color:#000;fontWeight:blod;padding-left:3px;padding-right:3px;";
    this.rightParen = document.createElement("span");
    this.rightParen.textContent = ")";
    this.rightParen.style.cssText = "color:#000;fontWeight:blod;padding-left:3px;padding-right:3px;";
    this.parenContainer = document.createElement("span");
    this.container.appendChild(this.leftParen);
    this.container.appendChild(this.parenContainer);
    this.container.appendChild(this.rightParen);
    this.inputType = new ruleforge.InputType(null, null, null, rule);
    this.parenContainer.appendChild(this.inputType.getContainer());
    this.arithmetic = new ruleforge.ComplexArithmetic(rule);
    this.container.appendChild(this.arithmetic.getContainer());
};
ruleforge.Paren.prototype.initData = function (data) {
    var value = data["value"];
    var valueType = value["valueType"];
    this.inputType.setValueType(valueType, value);
    this.arithmetic.initData(data["arithmetic"]);
};

ruleforge.Paren.prototype.getDisplayContainer = function () {
    return this.inputType.getDisplayContainer();
};

ruleforge.Paren.prototype.toXml = function () {
    if (!this.inputType) {
        throw "请输入括号内容!";
    }
    var xml = "<paren>";
    xml += this.inputType.toXml();
    if (this.arithmetic) {
        xml += this.arithmetic.toXml();
    }
    xml += "</paren>";
    return xml;
};
ruleforge.Paren.prototype.getContainer = function () {
    return this.container;
};