ruleforge.NextType = function (rule) {
    this.container = document.createElement("span");
    this.rule = rule;
    this.inputType = null;
    this.paren = null;
    this.selectorLabel = generateContainer();
    this.selectorLabel.style.fontWeight = "blod";
    this.selectorLabel.style.color = "#fff";
    this.container.appendChild(this.selectorLabel);
    RuleForge.setDomContent(this.selectorLabel, ".");
    var self = this;
    var onClick = function (menu) {
        var type = menu.name;
        self.doNext(type);
        window._setDirty();
    };
    self.menu = new RuleForge.menu.Menu({
        menuItems: [{
            label: "值",
            name: "value",
            onClick: onClick
        }, {
            label: "括号",
            name: "paren",
            onClick: onClick
        }]
    });
    this.selectorLabel.addEventListener("click", function (e) {
        self.menu.show(e);
    });
};
ruleforge.NextType.prototype.initData = function (data) {
    var value = data["value"];
    var valueType = value["valueType"];
    if (valueType == "Paren") {
        this.doNext("paren");
        this.paren.initData(value);
    } else {
        this.doNext("value");
        this.inputType.setValueType(valueType, value);
    }
};
ruleforge.NextType.prototype.toXml = function () {
    if (this.paren) {
        return this.paren.toXml();
    } else if (this.inputType) {
        return this.inputType.toXml();
    }
    return "";
};
ruleforge.NextType.prototype.getDisplayContainer = function () {
    if (this.inputType) {
        return this.inputType.getDisplayContainer();
    } else if (this.paren) {
        return this.paren.getDisplayContainer();
    }
    return null;
};
ruleforge.NextType.prototype.getContainer = function () {
    return this.container;
};
ruleforge.NextType.prototype.doNext = function (type) {
    if (type == "value") {
        if (this.paren) {
            this.paren.getContainer().remove();
            this.paren = null;
        }
        if (!this.inputType) {
            this.inputType = new ruleforge.InputType(null, null, null, this.rule);
            this.container.appendChild(this.inputType.getContainer());
        }
    } else if (type == "paren") {
        if (this.inputType) {
            this.inputType.getContainer().remove();
            this.inputType = null;
        }
        if (!this.paren) {
            this.paren = new ruleforge.Paren(this.rule);
            this.container.appendChild(this.paren.getContainer());
        }
    }
};