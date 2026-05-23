ruleforge.MethodParameter = function (rule) {
    this.inputType = new ruleforge.InputType(null, null, null, rule);
    this.container = this.inputType.getContainer();
};
ruleforge.MethodParameter.prototype.initData = function (data) {
    if (!data) {
        return;
    }
    this.name = data["name"];
    this.type = data["type"];
    if (!data["value"]) {
        return;
    }
    var value = data["value"];
    if (!value["valueType"]) {
        return;
    }
    this.inputType.setValueType(value["valueType"], value);
};

ruleforge.MethodParameter.prototype.toXml = function () {
    var xml = "<parameter name=\"" + this.name + "\" type=\"" + this.type + "\">";
    xml += this.inputType.toXml();
    xml += "</parameter>";
    return xml;
};
ruleforge.MethodParameter.prototype.getContainer = function () {
    return this.container;
};
ruleforge.MethodParameter.prototype.getInputType = function () {
    return this.inputType;
};