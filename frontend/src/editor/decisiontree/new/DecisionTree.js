import Context from './Context.js';
import VariableNode from './VariableNode.js';

export default class DecisionTree {
    constructor(container) {
        this.container = container;
        this.properties = [];
        this.initRemarkContainer();
        this.initPropertyContainer();

        var treeContainer = document.createElement("div");
        treeContainer.style.cssText = "position: relative;text-align: center";
        this.container.appendChild(treeContainer);
        var context = new Context(treeContainer);
        this.topNode = new VariableNode(context, null, true);
        context.topNode = this.topNode;
        var left = 10, top = 10;
        this.topNode.nodeContainer.style.position = "absolute";
        this.topNode.nodeContainer.style.left = left + "px";
        this.topNode.nodeContainer.style.top = top + "px";
    }

    initRemarkContainer() {
        var remarkContainer = document.createElement("div");
        remarkContainer.style.cssText = "margin: 5px;";
        this.container.appendChild(remarkContainer);
        this.remark = new Remark(remarkContainer);
    }

    initPropertyContainer() {
        var propContainer = document.createElement("div");
        propContainer.style.cssText = "margin: 5px;border: solid 1px #dddddd;border-radius:5px";
        this.container.appendChild(propContainer);
        this.propertyContainer = document.createElement("span");
        this.propertyContainer.style.padding = "10px";
        var addProp = document.createElement("button");
        addProp.type = "button";
        addProp.className = "rule-add-property btn btn-link";
        addProp.textContent = "添加属性";
        propContainer.appendChild(addProp);
        propContainer.appendChild(this.propertyContainer);
        var self = this;
        var onClick = function (menuItem) {
            var prop = new ruleforge.RuleProperty(self, menuItem.name, menuItem.defaultValue, menuItem.editorType);
            self.addProperty(prop);
        };
        self.menu = new RuleForge.menu.Menu({
            menuItems: [{
                label: "优先级",
                name: "salience",
                defaultValue: "10",
                editorType: 1,
                onClick: onClick
            }, {
                label: "生效日期",
                name: "effective-date",
                defaultValue: "",
                editorType: 2,
                onClick: onClick
            }, {
                label: "失效日期",
                name: "expires-date",
                defaultValue: "",
                editorType: 2,
                onClick: onClick
            }, {
                label: "是否启用",
                name: "enabled",
                defaultValue: true,
                editorType: 3,
                onClick: onClick
            }, {
                label: "允许调试信息输出",
                name: "debug",
                defaultValue: true,
                editorType: 3,
                onClick: onClick
            }]
        });
        addProp.addEventListener('click', function (e) {
            self.menu.show(e);
        });
    }

    addProperty(property) {
        this.propertyContainer.appendChild(property.getContainer());
        this.properties.push(property);
        window._setDirty();
    };

    toXml() {
        var xml = '<?xml version="1.0" encoding="UTF-8"?>';
        xml += '<decision-tree';
        for (var i = 0; i < this.properties.length; i++) {
            var prop = this.properties[i];
            xml += " " + prop.toXml();
        }
        xml += ">";
        xml += this.remark.toXml();
        parameterLibraries.forEach(function (item) {
            xml += '<import-parameter-library path="' + item + '"/>';
        });
        variableLibraries.forEach(function (item) {
            xml += '<import-variable-library path="' + item + '"/>';
        });
        constantLibraries.forEach(function (item) {
            xml += '<import-constant-library path="' + item + '"/>';
        });
        actionLibraries.forEach(function (item) {
            xml += '<import-action-library path="' + item + '"/>';
        });
        xml += this.topNode.toXml();
        xml += '</decision-tree>';
        return xml;
    }

    loadData(treeData) {
        this.remark.setData(treeData["remark"]);
        var salience = treeData["salience"];
        if (salience) {
            this.addProperty(new ruleforge.RuleProperty(this, "salience", salience, 1));
        }
        var loop = treeData["loop"];
        if (loop != null) {
            this.addProperty(new ruleforge.RuleProperty(this, "loop", loop, 3));
        }
        var effectiveDate = treeData["effectiveDate"];
        if (effectiveDate) {
            this.addProperty(new ruleforge.RuleProperty(this, "effective-date", effectiveDate, 2));
        }
        var expiresDate = treeData["expiresDate"];
        if (expiresDate) {
            this.addProperty(new ruleforge.RuleProperty(this, "expires-date", expiresDate, 2));
        }
        var enabled = treeData["enabled"];
        if (enabled != null) {
            this.addProperty(new ruleforge.RuleProperty(this, "enabled", enabled, 3));
        }
        var debug = treeData["debug"];
        if (debug != null) {
            this.addProperty(new ruleforge.RuleProperty(this, "debug", debug, 3));
        }

        var libraries = treeData["libraries"];
        if (libraries) {
            for (var i = 0; i < libraries.length; i++) {
                var lib = libraries[i];
                var type = lib["type"];
                var path = lib["path"];
                switch (type) {
                    case "Constant":
                        constantLibraries.push(path);
                        break;
                    case "Action":
                        actionLibraries.push(path);
                        break;
                    case "Variable":
                        variableLibraries.push(path);
                        break;
                    case "Parameter":
                        parameterLibraries.push(path);
                        break;
                }
            }
        }
        refreshActionLibraries();
        refreshConstantLibraries();
        refreshVariableLibraries();
        refreshParameterLibraries();
        refreshFunctionLibraries();
        this.topNode.initData(treeData["variableTreeNode"]);
    }
};
