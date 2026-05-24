ruleforge.ComplexArithmetic = function (rule) {
    this.container = document.createElement("span");
    this.operator = "";
    this.rule = rule;
    this.selectorLabel = generateContainer();
    this.selectorLabel.style.fontWeight = "blod";
    this.container.appendChild(this.selectorLabel);
    this.nextType = null;
    var self = this;
    var onClick = function (menu) {
        self.setOperator(menu.name);
    }
    self.menu = new RuleForge.menu.Menu({
        menuItems: [{
            label: "+",
            name: "Add",
            onClick: onClick
        }, {
            label: "-",
            name: "Sub",
            onClick: onClick
        }, {
            label: "x",
            name: "Mul",
            onClick: onClick
        }, {
            label: "÷",
            name: "Div",
            onClick: onClick
        }, {
            label: "%",
            name: "Mod",
            onClick: onClick
        }, {
            label: "删除",
            onClick: function () {
                window._setDirty();
                if (self.nextType) {
                    self.nextType.getContainer().remove();
                    self.nextType = null;
                    RuleForge.setDomContent(self.selectorLabel, ".");
                    self.selectorLabel.style.color = "#fff";
                    self.selectorLabel.style.paddingLeft = "0px";
                    self.selectorLabel.style.paddingRight = "0px";
                }
            }
        }]
    });
    this.selectorLabel.addEventListener("click", function (e) {
        self.menu.show(e);
    });

};
ruleforge.ComplexArithmetic.prototype.setOperator = function (operator) {
    window._setDirty();
    this.operator = operator;
    this.info = "";
    switch (operator) {
        case "Add":
            this.info = "+";
            break;
        case "Sub":
            this.info = "-";
            break;
        case "Mul":
            this.info = "x";
            break;
        case "Div":
            this.info = "÷";
            break;
        case "Mod":
            this.info = "%";
            break;
    }
    RuleForge.setDomContent(this.selectorLabel, this.info);
    this.selectorLabel.style.color = "green";
    this.selectorLabel.style.paddingLeft = "4px";
    this.selectorLabel.style.paddingRight = "4px";
    if (!this.nextType) {
        this.nextType = new ruleforge.NextType(this.rule);
        this.container.appendChild(this.nextType.getContainer());
    }
};
ruleforge.ComplexArithmetic.prototype.initData = function (data) {
    if (!data) {
        return;
    }
    var type = data["type"];
    this.setOperator(type);
    this.nextType.initData(data);
};
ruleforge.ComplexArithmetic.prototype.getDisplayContainer = function () {
    if (this.nextType) {
        var container = document.createElement("span");
        container.textContent = this.info;
        container.appendChild(this.nextType.getDisplayContainer());
        return container;
    }
    return null;
};
ruleforge.ComplexArithmetic.prototype.toXml = function () {
    if (!this.nextType) {
        return "";
    }
    var xml = "<complex-arith type=\"" + this.operator + "\">";
    xml += this.nextType.toXml();
    xml += "</complex-arith>";
    return xml;
};
ruleforge.ComplexArithmetic.prototype.getContainer = function () {
    return this.container;
};