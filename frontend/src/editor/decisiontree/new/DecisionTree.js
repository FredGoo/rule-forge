import {ajaxSave, getParameter} from '../../../Utils.js';
import Context from './Context.js';
import VariableNode from './VariableNode.js';
import {saveNewVersion} from "../../../Utils";
import * as event from '../../../components/componentEvent.js';

export default class DecisionTree {
    constructor(container) {
        this.container = container;
        this.properties = [];
        this.initToolbar();
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

    initToolbar() {
        var file = getParameter("file");
        var version = getParameter("version") || "";
        if (!file || file.length < 1) {
            alert("未指定具体的决策树文件！");
            return;
        }

        var saveButton = `<div class="btn-group btn-group-sm navbar-btn" style="margin-top:0;margin-bottom: 0" role="group" aria-label="...">
                <button id="saveButton" type="button" class="btn btn-default navbar-btn" ><i class="icon-save"/> 保存</button>
                <button id="saveButtonNewVersion" type="button" class="btn btn-default navbar-btn"><i class="rf rf-savenewversion"/> 生成版本</button>
            </div>`;
        var testButton = '<div class="btn-group btn-group-sm navbar-btn" style="margin-left:5px;margin-top:0;margin-bottom: 0" role="group" aria-label="...">' +
            '<button id="testButton" type="button" class="btn btn-success navbar-btn"><i class="glyphicon glyphicon-flash"/> 快速测试</button>' +
            '</div>';
        var toolbarHtml = `<nav class="navbar navbar-default" style="margin: 5px">
            <div>
                <div>
                    <div class="btn-group btn-group-sm navbar-btn" style="margin-left:5px;margin-top:0;margin-bottom: 0" role="group" aria-label="...">
                        <button id="configVarButton" type="button" class="btn btn-default"><i class="rf rf-variable"/> 变量库</button>
                        <button id="configConstantsButton" type="button" class="btn btn-default"><i class="rf rf-constant"/> 常量库</button>
                        <button id="configActionButton" type="button" class="btn btn-default"><i class="rf rf-action"/> 动作库</button>
                        <button id="configParameterButton" type="button" class="btn btn-default"><i class="rf rf-parameter"/> 参数库</button>
                    </div>
                    ${saveButton}
                    ${testButton}
                 </div>
            </div>
        </nav>`;
        var toolbarContainer = document.createElement("div");
        toolbarContainer.innerHTML = toolbarHtml;
        this.container.appendChild(toolbarContainer.firstElementChild || toolbarContainer);
        var self = this;
        document.getElementById("configVarButton").addEventListener('click', function () {
            if (!self.configVarDialog) {
                self.configVarDialog = new ruleforge.ConfigVariableDialog(self);
            }
            self.configVarDialog.open();
        });

        document.getElementById("configConstantsButton").addEventListener('click', function () {
            if (!self.configConstantDialog) {
                self.configConstantDialog = new ruleforge.ConfigConstantDialog(self);
            }
            self.configConstantDialog.open();
        });

        document.getElementById("configActionButton").addEventListener('click', function () {
            if (!self.configActionDialog) {
                self.configActionDialog = new ruleforge.ConfigActionDialog(self);
            }
            self.configActionDialog.open();
        });

        document.getElementById("configParameterButton").addEventListener('click', function () {
            if (!self.configParameterDialog) {
                self.configParameterDialog = new ruleforge.ConfigParameterDialog(self);
            }
            self.configParameterDialog.open();
        });

        var saveButtonNewVersion = document.getElementById("saveButtonNewVersion");
        saveButtonNewVersion.addEventListener('click', function () {
            _save(true);
        });
        var saveButtonOldVersion = document.getElementById("saveButton");
        saveButtonOldVersion.addEventListener('click', function () {
            _save(false);
        });
        saveButtonOldVersion.classList.add("disabled");
        _loadDecisionTreeFileData();

        document.getElementById("testButton").addEventListener('click', function() {
            let file = getParameter('file')
            let decodedFile = decodeURIComponent(file)
            event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile});
        })

        function _save(newVersion) {
            if (!newVersion && saveButtonOldVersion.classList.contains("disabled")) {
                return false;
            }
            var xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
            xml += "<decision-tree";
            for (var i = 0; i < self.properties.length; i++) {
                var prop = self.properties[i];
                xml += " " + prop.toXml();
            }
            xml += ">";
            xml += self.remark.toXml();
            parameterLibraries.forEach(function (item) {
                xml += "<import-parameter-library path=\"" + item + "\"/>";
            });
            variableLibraries.forEach(function (item) {
                xml += "<import-variable-library path=\"" + item + "\"/>";
            });
            constantLibraries.forEach(function (item) {
                xml += "<import-constant-library path=\"" + item + "\"/>";
            });
            actionLibraries.forEach(function (item) {
                xml += "<import-action-library path=\"" + item + "\"/>";
            });
            try {
                xml += self.topNode.toXml();
            } catch (error) {
                alert(error);
                return;
            }
            xml += "</decision-tree>";
            xml = encodeURIComponent(xml);
            let postData = {content: xml, file, newVersion};
            const url = window._server + '/common/saveFile';
            if (newVersion) {
                saveNewVersion(url, postData, function () {
                    cancelDirty();
                    bootbox.alert('保存成功!');
                });
            } else {
                ajaxSave(url, postData, function () {
                    cancelDirty();
                    bootbox.alert('保存成功!');
                });
            }
        }

        function _loadDecisionTreeFileData() {
            var url = window._server + '/common/loadXml';
            fetch(url, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: new URLSearchParams({files: file}).toString()
            }).then(function(response) {
                if (!response.ok) throw response;
                return response.json();
            }).then(function (data) {
                var treeData = data[0];
                self.remark.setData(treeData["remark"]);
                var salience = treeData["salience"];
                if (salience) {
                    self.addProperty(new ruleforge.RuleProperty(self, "salience", salience, 1));
                }
                var loop = treeData["loop"];
                if (loop != null) {
                    self.addProperty(new ruleforge.RuleProperty(self, "loop", loop, 3));
                }
                var effectiveDate = treeData["effectiveDate"];
                if (effectiveDate) {
                    self.addProperty(new ruleforge.RuleProperty(self, "effective-date", effectiveDate, 2));
                }
                var expiresDate = treeData["expiresDate"];
                if (expiresDate) {
                    self.addProperty(new ruleforge.RuleProperty(self, "expires-date", expiresDate, 2));
                }
                var enabled = treeData["enabled"];
                if (enabled != null) {
                    self.addProperty(new ruleforge.RuleProperty(self, "enabled", enabled, 3));
                }
                var debug = treeData["debug"];
                if (debug != null) {
                    self.addProperty(new ruleforge.RuleProperty(self, "debug", debug, 3));
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
                self.topNode.initData(treeData["variableTreeNode"]);
                cancelDirty();
            }).catch(function (response) {
                if (response && response.status === 401) {
                    bootbox.alert("权限不足，不能进行此操作.");
                } else if (response && response.text) {
                    response.text().then(function(text) {
                        bootbox.alert("<span style='color: red'>加载文件失败：" + text + "</span>");
                    });
                } else {
                    alert("加载文件失败！");
                }
            });
        }
    }
};

window._setDirty = function () {
    if (window._dirty) {
        return;
    }
    window._dirty = true;

    var saveButton = document.getElementById("saveButton");
    saveButton.innerHTML = "<i class='rf rf-save'/> *保存";
    saveButton.classList.remove("disabled");
};

function cancelDirty() {
    if (!window._dirty) {
        return;
    }
    window._dirty = false;

    var saveButton = document.getElementById("saveButton");
    saveButton.innerHTML = "<i class='rf rf-save'/> 保存";
    saveButton.classList.add("disabled");
}
