ruleforge.ComplexArithmetic = function (rule) {
    this.container = $("<span>");
    this.operator = "";
    this.rule = rule;
    this.selectorLabel = generateContainer();
    this.selectorLabel.css({
        "fontWeight": "blod"
    });
    this.container.append(this.selectorLabel);
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
                    self.selectorLabel.css({
                        "color": "#fff",
                        "padding-left": "0px",
                        "padding-right": "0px"
                    });
                }
            }
        }]
    });
    this.selectorLabel.click(function (e) {
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
    this.selectorLabel.css({
        "color": "green",
        "padding-left": "4px",
        "padding-right": "4px"
    });
    if (!this.nextType) {
        this.nextType = new ruleforge.NextType(this.rule);
        this.container.append(this.nextType.getContainer());
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
        var container = $("<span>" + this.info + "</span>");
        container.append(this.nextType.getDisplayContainer());
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