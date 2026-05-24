ruleforge.FunctionValue = function (arithmetic, data, rule) {
    this.arithmetic = arithmetic;
    this.container = document.createElement("span");
    this.rule = rule;
    this.leftParn = document.createElement("span");
    this.leftParn.style.color = "blue";
    this.leftParn.textContent = "(";
    this.rightParn = document.createElement("span");
    this.rightParn.style.color = "blue";
    this.rightParn.textContent = ")";
    this.label = generateContainer();
    this.container.appendChild(this.label);
    this.label.style.color = "#008080";
    this.functionContainer = document.createElement("span");
    this.container.appendChild(this.functionContainer);
    RuleForge.setDomContent(this.label, "请选择函数");
    if (arithmetic) {
        this.container.appendChild(arithmetic.getContainer());
    }
    if (data) {
        this.setFunction(data);
        if (arithmetic) {
            arithmetic.initData(data["arithmetic"]);
        }
    }
    window._FunctionValueArray.push(this);
    this.initMenu();
};

ruleforge.FunctionValue.prototype.getDisplayContainer = function () {
    var container = document.createElement("span");
    container.textContent = this.functionName;
    if (this.arithmetic) {
        var dis = this.arithmetic.getDisplayContainer();
        if (dis) {
            container.appendChild(dis);
        }
    }
    return container;
};
ruleforge.FunctionValue.prototype.toXml = function () {
    if (!this.functionLabel) {
        throw "请选择函数";
    }
    if (!this.functionName) {
        throw "请选择函数";
    }
    var xml = " function-label=\"" + this.functionLabel + "\"";
    xml += " function-name=\"" + this.functionName + "\"";
    return xml;
};

ruleforge.FunctionValue.prototype.initMenu = function (functionLibraries) {
    var data = window._ruleforgeEditorFunctionLibraries;
    if (functionLibraries) {
        data = functionLibraries;
    }
    var self, onClick, config;
    self = this;
    onClick = function (menuItem) {
        self.setFunction({
            parameter: menuItem.parameter,
            label: menuItem.label,
            name: menuItem.name
        });
    };
    config = {menuItems: []};
    data || [].forEach(function(item) {
        config.menuItems.push({
            name: item.name,
            label: item.label,
            parameter: item.argument,
            onClick: onClick
        });
    });
    if (self.menu) {
        self.menu.setConfig(config);
    } else {
        self.menu = new RuleForge.menu.Menu(config);
    }
    this.label.addEventListener("click", function (e) {
        self.menu.show(e);
    });
};
ruleforge.FunctionValue.prototype.initData = function (data) {
    if (data) {
        this.setFunction(data);
    }
};

ruleforge.FunctionValue.prototype.setFunction = function (data) {
    window._setDirty();
    this.functionContainer.innerHTML = "";
    RuleForge.setDomContent(this.label, data.label);
    this.functionContainer.appendChild(this.leftParn);
    this.functionLabel = data.label;
    this.functionName = data.name;
    this.parameter = new ruleforge.FunctionParameter(this.rule);
    this.parameter.initData(data.parameter);
    this.functionContainer.appendChild(this.parameter.getContainer());
    this.functionContainer.appendChild(this.rightParn);
};
ruleforge.FunctionValue.prototype.getFirstParameter = function () {
    return this.firstParameter;
};

ruleforge.FunctionValue.prototype.getParameter = function () {
    return this.parameter;
};

ruleforge.FunctionValue.prototype.getContainer = function () {
    return this.container;
};