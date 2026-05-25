ruleforge.SimpleArithmetic = function () {
    this.container = document.createElement("span");
    this.selectorLabel = generateContainer();
    this.selectorLabel.textContent = ".";
    this.selectorLabel.style.color = "#FFF";
    this.operator = "";
    this.container.appendChild(this.selectorLabel);
    this.value = null;
    var self = this;
    var onClick = function (menuItem) {
        self.setOperator(menuItem.name);
    };
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
                if (self.value) {
                    self.value.getContainer().remove();
                    self.operator = null;
                    self.value = null;
                    self.selectorLabel.textContent = ".";
                    self.selectorLabel.style.color = "#FFF";
                    self.selectorLabel.style.paddingLeft = "0px";
                    self.selectorLabel.style.paddingRight = "0px";
                }
            }
        }]
    });
    this.selectorLabel.addEventListener('click', function (e) {
        self.menu.show(e);
    });

};
ruleforge.SimpleArithmetic.prototype.initData = function (data) {
    if (!data) {
        return;
    }
    var type = data["type"];
    this.setOperator(type);
    this.value.initData(data["value"]);
};
ruleforge.SimpleArithmetic.prototype.setOperator = function (operator) {
    window._setDirty();
    this.operator = operator;
    var info = "";
    switch (operator) {
        case "Add":
            info = "+";
            break;
        case "Sub":
            info = "-";
            break;
        case "Mul":
            info = "x";
            break;
        case "Div":
            info = "÷";
            break;
        case "Mod":
            info = "%";
            break;
    }
    this.selectorLabel.style.color = "green";
    this.selectorLabel.style.fontWeight = "bold";
    this.selectorLabel.style.paddingLeft = "5px";
    this.selectorLabel.style.paddingRight = "5px";
    this.selectorLabel.textContent = info;
    if (!this.value) {
        this.simpleArithmetic = new ruleforge.SimpleArithmetic();
        this.value = new ruleforge.SimpleValue(this.simpleArithmetic);
        this.container.appendChild(this.value.getContainer());
    }
};
ruleforge.SimpleArithmetic.prototype.toXml = function () {
    if (!this.operator || this.operator == "") {
        return "";
    }
    if (!this.value) {
        throw "请输入具体值！";
    }
    var value = this.value.getValue();
    if (value == "") {
        throw "请输入具体值！";
    }
    var xml = "<simple-arith type=\"" + this.operator + "\" value=\"" + value + "\">";
    xml += this.simpleArithmetic.toXml();
    xml += "</simple-arith>";
    return xml;
};
ruleforge.SimpleArithmetic.prototype.getContainer = function () {
    return this.container;
};