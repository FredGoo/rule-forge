ruleforge.CellContent = function (element) {
    this.container = element[0] || element;
    this.container.style.height = "40px";
    this.container.style.width = "100%";
    this.inputType = new ruleforge.InputType(null, "无");
    this.container.appendChild(this.inputType.getContainer());
};
ruleforge.CellContent.prototype.clean = function (data) {
    if (this.inputType) {
        this.inputType.getContainer().remove();
    }
    this.inputType = new ruleforge.InputType(null, "无");
    this.container.appendChild(this.inputType.getContainer());
    window._setDirty();
};
ruleforge.CellContent.prototype.initData = function (data) {
    this.inputType.setValueType(data["valueType"], data);
};
ruleforge.CellContent.prototype.toXml = function () {
    if (this.inputType.type == "") {
        return "";
    }
    return this.inputType.toXml();
};
