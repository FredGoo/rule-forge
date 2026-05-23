ruleforge.ParameterValue = function (arithmetic, data, act) {
    this.arithmetic = arithmetic;
    this.container = $("<span>");
    this.label = generateContainer();
    this.container.append(this.label);
    this.label.css({
        "color": "#6b3db0"
    });
    RuleForge.setDomContent(this.label, "请选择参数");
    if (arithmetic) {
        this.container.append(arithmetic.getContainer());
    }
    if (data) {
        this.initData(data);
    }
    window._ParameterValueArray.push(this);
    this.act = act;
    this.initMenu();
};

ruleforge.ParameterValue.prototype.getDisplayContainer = function () {
    var container = $("<span>参数." + this.parameterLabel + "</span>");
    if (this.arithmetic) {
        var dis = this.arithmetic.getDisplayContainer();
        if (dis) {
            container.append(dis);
        }
    }
    return container;
};

ruleforge.ParameterValue.prototype.matchAct = function (act) {
    if (!this.act) {
        return true;
    }
    if (act.indexOf(this.act) > -1) {
        return true;
    }
    return false;
};
ruleforge.ParameterValue.prototype.initMenu = function (parameterLibraries) {
    var data = window._ruleforgeEditorParameterLibraries;
    if (parameterLibraries) {
        data = parameterLibraries;
    }
    if (!data) {
        return;
    }
    var self, onClick, config;
    self = this;
    onClick = function (menuItem) {
        self.setValue({
            variableName: menuItem.name,
            variableLabel: menuItem.label,
            datatype: menuItem.datatype
        });

    };
    config = {menuItems: []};
    data.forEach(function(variables) {
        variables || [].forEach(function(variable) {
            if (self.matchAct(variable.act)) {
                var menuItem = {
                    name: variable.name,
                    label: variable.label,
                    datatype: variable.type,
                    act: variable.act,
                    onClick: onClick
                };
                config.menuItems.push(menuItem);
            }

        });
    });
    if (self.menu) {
        self.menu.setConfig(config);
    } else {
        self.menu = new RuleForge.menu.Menu(config);
    }
    this.label.click(function (e) {
        self.menu.show(e);
    });
};
ruleforge.ParameterValue.prototype.setValue = function (data) {
    this.parameterName = data["variableName"];
    this.parameterLabel = data["variableLabel"];
    this.datatype = data["datatype"];
    RuleForge.setDomContent(this.label, "参数." + this.parameterLabel);
    window._setDirty();
};
ruleforge.ParameterValue.prototype.initData = function (data) {
    this.setValue(data);
    if (this.arithmetic) {
        this.arithmetic.initData(data["arithmetic"]);
    }
};

ruleforge.ParameterValue.prototype.toXml = function () {
    if (!this.parameterLabel || this.parameterLabel == "") {
        throw "参数不能为空！";
    }
    var xml = " var-category=\"参数\" var=\"" + this.parameterName + "\" var-label=\"" + this.parameterLabel + "\" datatype=\"" + this.datatype + "\"";
    return xml;
};
ruleforge.ParameterValue.prototype.getContainer = function () {
    return this.container;
};