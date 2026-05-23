ruleforge.FunctionParameter = function (rule) {
    this.container = $("<span>");
    this.nameContainer = $("<span>");
    this.rule = rule;
    this.container.append(this.nameContainer);
    this.nameContainer.css({
        "color": "gray"
    });
};
ruleforge.FunctionParameter.prototype.initData = function (data) {
    if (!data) {
        return;
    }
    this.name = data.name;
    RuleForge.setDomContent(this.nameContainer, this.name + ":");
    if (data.needProperty || data.property) {
        this.functionProperty = new ruleforge.FunctionProperty();
        this.functionProperty.setProperty({name: data.property, label: data.propertyLabel});
    }
    this.inputType = new ruleforge.InputType(null, null, this.functionProperty, this.rule);
    var value = data.objectParameter;
    if (value) {
        var valueType = value.valueType;
        this.inputType.setValueType(valueType, value);
    }
    this.container.append(this.inputType.getContainer());
    if (this.functionProperty) {
        this.container.append($("<span>，</span>"));
        this.container.append($("<span style='color:gray'>属性:</span>"));
        this.container.append(this.functionProperty.getContainer());
    }
};
ruleforge.FunctionParameter.prototype.toXml = function () {
    if (!this.name) {
        return "";
    }
    var xml = "<function-parameter ";
    xml += "name=\"" + this.name + "\" ";
    if (this.functionProperty) {
        xml += this.functionProperty.toXml();
    }
    xml += ">";
    xml += this.inputType.toXml();
    xml += "</function-parameter>";
    return xml;
};
ruleforge.FunctionParameter.prototype.getContainer = function () {
    return this.container;
};