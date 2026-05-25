ruleforge.FunctionParameter = function (rule) {
    this.container = document.createElement("span");
    this.nameContainer = document.createElement("span");
    this.rule = rule;
    this.container.appendChild(this.nameContainer);
    this.nameContainer.style.color = "gray";
};
ruleforge.FunctionParameter.prototype.initData = function (data) {
    if (!data) {
        return;
    }
    this.name = data.name;
    this.nameContainer.textContent = this.name + ":";
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
    this.container.appendChild(this.inputType.getContainer());
    if (this.functionProperty) {
        var commaSpan = document.createElement("span");
        commaSpan.textContent = "，";
        this.container.appendChild(commaSpan);
        var propLabel = document.createElement("span");
        propLabel.style.color = "gray";
        propLabel.textContent = "属性:";
        this.container.appendChild(propLabel);
        this.container.appendChild(this.functionProperty.getContainer());
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