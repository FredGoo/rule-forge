import '../bootbox.js';
import ScoreCardTable from './ScoreCardTable.js';
import '../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../css/iconfont.css';
import './scorecard.css';
import '../editor/context.standalone.css';
import '../editor/ruleforge/ruleset.css';
import React from 'react';
import { createRoot } from 'react-dom/client';
import '../Remark.js';
import '../editor/common/jquery.utils.js';
import '../editor/common/URule.js';
import '../editor/common/contextMenu.js';
import '../editor/Math.uuid.js';
import '../editor/common/Context.js';
import '../editor/decisiontable/CellCondition.js';
import '../editor/decisiontable/Condition.js';
import '../editor/decisiontable/Join.js';
import '../editor/decisiontable/Connection.js';

import '../editor/common/ComparisonOperator.js';
import '../editor/common/ComplexArithmetic.js';
import '../editor/common/VariableValue.js';
import '../editor/common/ConstantValue.js';

import '../editor/ruleforge/SimpleArithmetic.js';
import '../editor/common/InputType.js';
import '../editor/common/NextType.js';
import '../editor/common/Paren.js';
import '../editor/common/MethodParameter.js';
import '../editor/common/MethodAction.js';
import '../editor/common/ParameterValue.js';
import '../editor/common/MethodValue.js';
import '../editor/common/FunctionProperty.js';
import '../editor/common/FunctionParameter.js';
import '../editor/common/FunctionValue.js';
import '../editor/common/SimpleValue.js';

import '../editor/ruleforge/ConfigActionDialog.js';
import '../editor/ruleforge/ConfigConstantDialog.js';
import '../editor/ruleforge/ConfigParameterDialog.js';
import '../editor/ruleforge/ConfigVariableDialog.js';
import '../editor/ruleforge/RuleProperty.js';
import * as event from '../components/componentEvent.js';
import QuickTestDialog from '../components/dialog/component/QuickTestDialog.jsx';
import {saveNewVersion} from "../Utils";

import {ajaxSave, buildProjectNameFromFile, getParameter} from '../Utils.js';

import KnowledgeTreeDialog from '../components/dialog/component/KnowledgeTreeDialog.jsx';

document.addEventListener('DOMContentLoaded', function (e) {
    const file = getParameter("file");
    if (!file) {
        alert("未指定文件.");
        return;
    }
    window._project = buildProjectNameFromFile(file);

    const toolbarContainer = document.getElementById("toolbarContainer");
    const toolbar = document.createElement('div');
    toolbar.className = 'btn-toolbar';
    toolbar.style.cssText = 'border: solid 1px #d0d0d0;padding:5px;margin:3px;border-radius: 5px;background: #fdfdfd;display:flex;align-items: center;';

    toolbarContainer.appendChild(toolbar);
    const operationGroup = document.createElement('div');
    operationGroup.className = 'btn-group btn-group-sm';
    toolbar.appendChild(operationGroup);
    const addAttributeButton = document.createElement('button');
    addAttributeButton.type = 'button';
    addAttributeButton.className = 'btn btn-default';
    addAttributeButton.innerHTML = "<i class='glyphicon glyphicon-plus'/> 添加属性行";
    operationGroup.appendChild(addAttributeButton);

    const addCustomColButton = document.createElement('button');
    addCustomColButton.type = 'button';
    addCustomColButton.className = 'btn btn-default';
    addCustomColButton.innerHTML = "<i class='glyphicon glyphicon-plus-sign'/> 添加自定义列";
    operationGroup.appendChild(addCustomColButton);

    var self = this;
    const configGroup = document.createElement('div');
    configGroup.className = 'btn-group btn-group-sm';
    toolbar.appendChild(configGroup);
    const variableButton = document.createElement('button');
    variableButton.type = 'button';
    variableButton.className = 'btn btn-default';
    variableButton.innerHTML = "<i class='rf rf-variable'/> 变量库";
    configGroup.appendChild(variableButton);
    variableButton.addEventListener('click', function () {
        if (!self.configVarDialog) {
            self.configVarDialog = new ruleforge.ConfigVariableDialog(self);
        }
        self.configVarDialog.open();
    });

    const constButton = document.createElement('button');
    constButton.type = 'button';
    constButton.className = 'btn btn-default';
    constButton.innerHTML = "<i class='rf rf-constant'/> 常量库";
    configGroup.appendChild(constButton);
    constButton.addEventListener('click', function () {
        if (!self.configConstantDialog) {
            self.configConstantDialog = new ruleforge.ConfigConstantDialog(self);
        }
        self.configConstantDialog.open();
    });

    const actionButton = document.createElement('button');
    actionButton.type = 'button';
    actionButton.className = 'btn btn-default';
    actionButton.innerHTML = "<i class='rf rf-action'/> 动作库";
    configGroup.appendChild(actionButton);
    actionButton.addEventListener('click', function () {
        if (!self.configActionDialog) {
            self.configActionDialog = new ruleforge.ConfigActionDialog(self);
        }
        self.configActionDialog.open();
    });

    const parameterButton = document.createElement('button');
    parameterButton.type = 'button';
    parameterButton.className = 'btn btn-default';
    parameterButton.innerHTML = "<i class='rf rf-parameter'/> 参数库";
    configGroup.appendChild(parameterButton);
    parameterButton.addEventListener('click', function () {
        if (!self.configParameterDialog) {
            self.configParameterDialog = new ruleforge.ConfigParameterDialog(self);
        }
        self.configParameterDialog.open();
    });

    const saveGroup = document.createElement('div');
    saveGroup.className = 'btn-group btn-group-sm';
    toolbar.appendChild(saveGroup);
    const saveButton = document.createElement('button');
    saveButton.type = 'button';
    saveButton.className = 'btn btn-default disabled';
    saveButton.innerHTML = "<i class='rf rf-save'/> 保存";
    saveGroup.appendChild(saveButton);
    const saveVersionButton = document.createElement('button');
    saveVersionButton.type = 'button';
    saveVersionButton.className = 'btn btn-default';
    saveVersionButton.innerHTML = "<i class='rf rf-savenewversion'/> 生成版本";
    saveGroup.appendChild(saveVersionButton);

    var testButtonDiv = document.createElement('div');
    testButtonDiv.className = 'btn-group btn-group-sm navbar-btn';
    testButtonDiv.style.cssText = 'margin-left:5px;margin-top:0;margin-bottom: 0';
    testButtonDiv.setAttribute('role', 'group');
    testButtonDiv.setAttribute('aria-label', '...');
    testButtonDiv.innerHTML = '<button id="testButton" type="button" class="btn btn-success navbar-btn"><i class="glyphicon glyphicon-flash"/> 快速测试</button>';
    toolbar.appendChild(testButtonDiv);
    document.getElementById("testButton").addEventListener('click', function() {
        let decodedFile = decodeURIComponent(file)
        event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile, type: 'scorecardLib'});
    });

    window._setDirty = function () {
        if (self._dirty) {
            return;
        }
        self._dirty = true;
        window._dirty = true;
        saveButton.innerHTML = "<i class='rf rf-save'/> *保存";
        saveButton.classList.remove("disabled");
    };

    function cancelDirty() {
        if (!self._dirty) {
            return;
        }
        self._dirty = false;
        window._dirty = false;
        saveButton.innerHTML = "<i class='rf rf-save'/> 保存";
        saveButton.classList.add("disabled");
    }

    addAttributeButton.addEventListener('click', function () {
        cardTable.addAttributeRow();
    });
    addCustomColButton.addEventListener('click', function () {
        cardTable.addCustomCol();
    });
    const cardTable = new ScoreCardTable({
        container: document.getElementById("tableContainer"),
        headers: []
    });

    function save(newVersion) {
        try {
            let content = cardTable.toXml();
            content = encodeURIComponent(content);
            const url = window._server + "/common/saveFile";
            if (newVersion) {
                let postData = {content, file, newVersion}
                saveNewVersion(url, postData, function () {
                    bootbox.alert("保存成功", function () {
                        cancelDirty();
                    });
                });
            } else {
                ajaxSave(url, {content, file, newVersion}, function () {
                    bootbox.alert("保存成功", function () {
                        cancelDirty();
                    });
                });
            }
        } catch (error) {
            bootbox.alert(error.message || error);
        }
    }

    saveButton.addEventListener('click', function () {
        save(false);
    });
    saveVersionButton.addEventListener('click', function () {
        save(true);
    });
    createRoot(document.getElementById("dialogContainer")).render(
        <div>
            <KnowledgeTreeDialog/>
            <QuickTestDialog/>
        </div>,
    );
    fetch(window._server + "/common/loadXml", {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files: file}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        const card = data[0];
        cardTable.init(card);
        var libraries = card.libraries;
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
                refreshActionLibraries();
                refreshConstantLibraries();
                refreshVariableLibraries();
                refreshParameterLibraries();
                refreshFunctionLibraries();
            }
            cancelDirty();
        }
    }).catch(function (response) {
        if (response && response.status === 401) {
            bootbox.alert("权限不足，不能进行此操作.");
        } else if (response && response.text) {
            response.text().then(function(text) {
                bootbox.alert("<span style='color: red'>加载数据失败,服务端错误：" + text + "</span>");
            });
        } else {
            bootbox.alert("<span style='color: red'>加载数据失败,服务端出错</span>");
        }
    });
});