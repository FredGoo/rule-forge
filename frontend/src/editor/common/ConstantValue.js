ruleforge.ConstantValue = function (arithmetic, data) {
    this.arithmetic = arithmetic;
    this.container = document.createElement("span");
    this.label = generateContainer();
    this.container.appendChild(this.label);
    this.label.style.color = "#0174DF";
    this.label.innerText = "请选择常量";
    if (arithmetic) {
        this.container.appendChild(arithmetic.getContainer());
    }
    if (data) {
        this.setValue(data);
        arithmetic.initData(data["arithmetic"]);
    }
    this.initMenu();
    window._ConstantValueArray.push(this);
};
ruleforge.ConstantValue.prototype.setValue = function (data) {
    this.category = data["constantCategory"];
    this.constantName = data["constantName"];
    this.constantLabel = data["constantLabel"];
    RuleForge.setDomContent(this.label, this.category + "." + this.constantLabel);
    window._setDirty();
};

ruleforge.ConstantValue.prototype.initMenu = function (constantLibraries) {
    var data = window._ruleforgeEditorConstantLibraries;
    if (constantLibraries) {
        data = constantLibraries;
    }
    if (!data) {
        return;
    }
    var self, onClick, config;
    self = this;
    onClick = function (menuItem) {
        self.setValue({
            constantCategory: menuItem.parent.parent.label,
            constantLabel: menuItem.label,
            constantName: menuItem.name
        });
    };
    config = {menuItems: []};
    data.forEach(function(item) {
        var categories = item["categories"];
        categories.forEach(function(category) {
            var menuItem = {
                label: category.label
            }
            var constants = category["constants"];
            constants.forEach(function(constant) {
                if (!menuItem.subMenu) {
                    menuItem.subMenu = {menuItems: []};
                }
                menuItem.subMenu.menuItems.push({
                    name: constant.name,
                    label: constant.label,
                    onClick: onClick
                });
            });
            config.menuItems.push(menuItem);
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

ruleforge.ConstantValue.prototype.getDisplayContainer = function () {
    var container = document.createElement("span");
    container.textContent = this.category + "." + this.constantLabel;
    if (this.arithmetic) {
        var dis = this.arithmetic.getDisplayContainer();
        if (dis) {
            container.appendChild(dis);
        }
    }
    return container;
};

ruleforge.ConstantValue.prototype.toXml = function () {
    if (!this.category) {
        throw "常量不能为空！";
    }
    var xml = "const-category=\"" + this.category + "\" const=\"" + this.constantName + "\" const-label=\"" + this.constantLabel + "\"";
    return xml;
};
ruleforge.ConstantValue.prototype.getContainer = function () {
    return this.container;
};