/**
 * Created by Jacky.gao on 2016/9/18.
 */
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

$(document).ready(function (e) {
    const file = getParameter("file");
    if (!file) {
        alert("未指定文件.");
        return;
    }
    window._project = buildProjectNameFromFile(file);

    const toolbarContainer = $("#toolbarContainer");
    const toolbar = $(`<div class="btn-toolbar" style="border: solid 1px #d0d0d0;padding:5px;margin:3px;border-radius: 5px;background: #fdfdfd;display:flex;align-items: center;"></div>`);

    toolbarContainer.append(toolbar);
    const operationGroup = $(`<div class="btn-group btn-group-sm"></div>`);
    toolbar.append(operationGroup);
    const addAttributeButton = $("<button type='button' class='btn btn-default'><i class='glyphicon glyphicon-plus'/> 添加属性行</button>");
    operationGroup.append(addAttributeButton);

    const addCustomColButton = $("<button type='button' class='btn btn-default'><i class='glyphicon glyphicon-plus-sign'/> 添加自定义列</button>");
    operationGroup.append(addCustomColButton);

    var self = this;
    const configGroup = $(`<div class="btn-group btn-group-sm"></div>`);
    toolbar.append(configGroup);
    const variableButton = $(`<button type="button" class="btn btn-default"><i class="rf rf-variable"/> 变量库</button>`);
    configGroup.append(variableButton);
    variableButton.click(function () {
        if (!self.configVarDialog) {
            self.configVarDialog = new ruleforge.ConfigVariableDialog(self);
        }
        self.configVarDialog.open();
    });

    const constButton = $(`<button type="button" class="btn btn-default"><i class="rf rf-constant"/> 常量库</button>`);
    configGroup.append(constButton);
    constButton.click(function () {
        if (!self.configConstantDialog) {
            self.configConstantDialog = new ruleforge.ConfigConstantDialog(self);
        }
        self.configConstantDialog.open();
    });

    const actionButton = $(`<button type="button" class="btn btn-default"><i class="rf rf-action"/> 动作库</button>`);
    configGroup.append(actionButton);
    actionButton.click(function () {
        if (!self.configActionDialog) {
            self.configActionDialog = new ruleforge.ConfigActionDialog(self);
        }
        self.configActionDialog.open();
    });

    const parameterButton = $(` <button type="button" class="btn btn-default"><i class="rf rf-parameter"/> 参数库</button>`);
    configGroup.append(parameterButton);
    parameterButton.click(function () {
        if (!self.configParameterDialog) {
            self.configParameterDialog = new ruleforge.ConfigParameterDialog(self);
        }
        self.configParameterDialog.open();
    });

    const saveGroup = $(`<div class="btn-group btn-group-sm"></div>`);
    toolbar.append(saveGroup);
    const saveButton = $(`<button type="button" class="btn btn-default disabled"><i class="rf rf-save"/> 保存</button>`);
    saveGroup.append(saveButton);
    const saveVersionButton = $(`<button type="button" class="btn btn-default"><i class="rf rf-savenewversion"/> 生成版本</button>`);
    saveGroup.append(saveVersionButton);

    var testButton = '<div class="btn-group btn-group-sm navbar-btn" style="margin-left:5px;margin-top:0;margin-bottom: 0" role="group" aria-label="...">' +
            '<button id="testButton" type="button" class="btn btn-success navbar-btn"><i class="glyphicon glyphicon-flash"/> 快速测试</button>' +
            '</div>';
    toolbar.append(testButton);
    $("#testButton").click(function() {
        let decodedFile = decodeURIComponent(file)
        event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile, type: 'scorecardLib'});
    })

    window._setDirty = function () {
        if (self._dirty) {
            return;
        }
        self._dirty = true;
        window._dirty = true;
        saveButton.html("<i class='rf rf-save'/> *保存");
        saveButton.removeClass("disabled");
    };

    function cancelDirty() {
        if (!self._dirty) {
            return;
        }
        self._dirty = false;
        window._dirty = false;
        saveButton.html("<i class='rf rf-save'/> 保存");
        saveButton.addClass("disabled");
    }

    addAttributeButton.click(function () {
        cardTable.addAttributeRow();
    });
    addCustomColButton.click(function () {
        cardTable.addCustomCol();
    });
    const cardTable = new ScoreCardTable({
        container: $("#tableContainer"),
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
                // bootbox.prompt("请输入新版本描述.", function (versionComment) {
                //     if (!versionComment) {
                //         return;
                //     }
                //     ajaxSave(url, {content, file, newVersion, versionComment}, function () {
                //         bootbox.alert("保存成功", function () {
                //             cancelDirty();
                //         });
                //     });

                // });
            } else {
                ajaxSave(url, {content, file, newVersion}, function () {
                    bootbox.alert("保存成功", function () {
                        cancelDirty();
                    });
                });
            }
        } catch (error) {
            bootbox.alert(error.message || error);
            //throw error;
        }
    }

    saveButton.click(function () {
        save(false);
    });
    saveVersionButton.click(function () {
        save(true);
    });
    createRoot(document.getElementById("dialogContainer")).render(
        <div>
            <KnowledgeTreeDialog/>
            <QuickTestDialog/>
        </div>,
);
    $.ajax({
        url: window._server + "/common/loadXml",
        type: "POST",
        data: {files: file},
        success: function (data) {
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
        },
        error: function (response) {
            if (response && response.responseText) {
                bootbox.alert("<span style='color: red'>加载数据失败,服务端错误：" + response.responseText + "</span>");
            } else {
                bootbox.alert("<span style='color: red'>加载数据失败,服务端出错</span>");
            }
        }
    });
});