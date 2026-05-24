import '../../bootbox.js';
/**
 * Crosstab Decision Table Editor - Entry point.
 *
 * Replaces the original jQuery-based bootstrap from the webpack bundle.
 * Initializes the CrossTable, toolbar, save/load logic, and Excel import.
 */

import '../../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../context.standalone.css';
import '../../css/iconfont.css';
import './crosstab.css';
import '../ruleforge/ruleset.css';
import '../Math.uuid.js';
import '../../Remark.js';
import '../common/contextMenu.js';
import '../common/URule.js';
import '../common/Context.js';
import '../common/ComparisonOperator.js';
import '../common/ComplexArithmetic.js';
import '../common/VariableValue.js';
import '../common/ResourceListDialog.js';
import '../common/ResourceVersionDialog.js';
import '../common/ConstantValue.js';
import '../ruleforge/ConfigActionDialog.js';
import '../ruleforge/ConfigConstantDialog.js';
import '../ruleforge/ConfigParameterDialog.js';
import '../ruleforge/ConfigVariableDialog.js';
import '../ruleforge/ActionType.js';
import '../ruleforge/PrintAction.js';
import '../ruleforge/AssignmentAction.js';
import '../ruleforge/SimpleArithmetic.js';
import '../ruleforge/RuleProperty.js';
import '../common/InputType.js';
import '../common/NextType.js';
import '../common/Paren.js';
import '../common/MethodParameter.js';
import '../common/MethodAction.js';
import '../common/ParameterValue.js';
import '../common/MethodValue.js';
import '../common/FunctionProperty.js';
import '../common/FunctionParameter.js';
import '../common/FunctionValue.js';
import '../common/SimpleValue.js';
import '../decisiontable/Join.js';
import '../decisiontable/Condition.js';
import '../decisiontable/CellCondition.js';
import '../common/jquery.utils.js';

import CrossTable from './CrossTable.js';
import ExcelImportDialog from './ExcelImportDialog.js';
import {getParameter, ajaxSave, buildProjectNameFromFile} from '../../Utils.js';
import React from 'react';
import {createRoot} from 'react-dom/client';
import ResourceVersionDialogComponent from '../common/ResourceVersionDialogComponent.jsx';
import ResourceListDialogComponent from '../common/ResourceListDialogComponent.jsx';

document.addEventListener('DOMContentLoaded', function () {
    const crossTable = new CrossTable({
        container: document.getElementById('container')
    });

    const file = getParameter('file');
    if (!file) {
        document.getElementById('container').innerHTML = '<h2 style="color:red">请先指定一个交叉决策表文件!</h2>';
        return;
    }

    window._project = buildProjectNameFromFile(file);

    // Mount React dialog components
    createRoot(document.getElementById('dialogContainer')).render(
        <div>
            <ResourceVersionDialogComponent/>
            <ResourceListDialogComponent/>
        </div>
    );

    // Build toolbar
    const toolbarContainer = document.getElementById('toolbarContainer');

    const toolbar = document.createElement('div');
    toolbar.className = 'btn-toolbar';
    toolbar.style.cssText = 'border: solid 1px #d0d0d0;padding:5px;margin:3px;border-radius: 5px;background: #fdfdfd';
    toolbarContainer.appendChild(toolbar);

    const group1 = document.createElement('div');
    group1.className = 'btn-group btn-group-sm';
    toolbar.appendChild(group1);

    // Save button (starts disabled)
    const saveButton = document.createElement('button');
    saveButton.type = 'button';
    saveButton.className = 'btn btn-default disabled';
    saveButton.innerHTML = "<i class='rf rf-save'></i> 保存";
    group1.appendChild(saveButton);

    // Save new version button (starts disabled)
    const saveNewVersionButton = document.createElement('button');
    saveNewVersionButton.type = 'button';
    saveNewVersionButton.className = 'btn btn-default disabled';
    saveNewVersionButton.innerHTML = "<i class='rf rf-savenewversion'></i> 保存新版本";
    group1.appendChild(saveNewVersionButton);

    const context = {};

    const group2 = document.createElement('div');
    group2.className = 'btn-group btn-group-sm';
    toolbar.appendChild(group2);

    // Variable library button
    const varButton = document.createElement('button');
    varButton.type = 'button';
    varButton.className = 'btn btn-default';
    varButton.innerHTML = "<i class='rf rf-variable'></i> 变量库";
    group2.appendChild(varButton);
    varButton.addEventListener('click', function () {
        if (!context.configVarDialog) {
            context.configVarDialog = new ruleforge.ConfigVariableDialog(context);
        }
        context.configVarDialog.open();
    });

    // Constant library button
    const constButton = document.createElement('button');
    constButton.type = 'button';
    constButton.className = 'btn btn-default';
    constButton.innerHTML = "<i class='rf rf-constant'></i> 常量库";
    group2.appendChild(constButton);
    constButton.addEventListener('click', function () {
        if (!context.configConstantDialog) {
            context.configConstantDialog = new ruleforge.ConfigConstantDialog(context);
        }
        context.configConstantDialog.open();
    });

    // Action library button
    const actionButton = document.createElement('button');
    actionButton.type = 'button';
    actionButton.className = 'btn btn-default';
    actionButton.innerHTML = "<i class='rf rf-action'></i> 动作库";
    group2.appendChild(actionButton);
    actionButton.addEventListener('click', function () {
        if (!context.configActionDialog) {
            context.configActionDialog = new ruleforge.ConfigActionDialog(context);
        }
        context.configActionDialog.open();
    });

    // Parameter library button
    const paramButton = document.createElement('button');
    paramButton.type = 'button';
    paramButton.className = 'btn btn-default';
    paramButton.innerHTML = "<i class='rf rf-parameter'></i> 参数库";
    group2.appendChild(paramButton);
    paramButton.addEventListener('click', function () {
        if (!context.configParameterDialog) {
            context.configParameterDialog = new ruleforge.ConfigParameterDialog(context);
        }
        context.configParameterDialog.open();
    });

    // Excel import button
    const excelButton = document.createElement('button');
    excelButton.type = 'button';
    excelButton.className = 'btn btn-default';
    excelButton.style.height = '36px';
    excelButton.innerHTML = "<i class='glyphicon glyphicon-share-alt' style='font-size: 16px'></i> 导入Excel";
    group2.appendChild(excelButton);
    excelButton.addEventListener('click', function () {
        new ExcelImportDialog().show();
    });

    // Dirty state management
    window._setDirty = function () {
        if (context._dirty) return;
        context._dirty = true;
        window._dirty = true;
        saveButton.innerHTML = "<i class='rf rf-save'></i> *保存";
        saveButton.classList.remove('disabled');
        saveNewVersionButton.innerHTML = "<i class='rf rf-savenewversion'></i> *保存新版本";
        saveNewVersionButton.classList.remove('disabled');
    };

    saveButton.addEventListener('click', function () {
        saveFile(false);
    });

    saveNewVersionButton.addEventListener('click', function () {
        saveFile(true);
    });

    /**
     * Clear the dirty state (after successful save).
     */
    function clearDirty() {
        context._dirty = false;
        window._dirty = false;
        saveButton.innerHTML = "<i class='rf rf-save'></i> 保存";
        saveButton.classList.add('disabled');
        saveNewVersionButton.innerHTML = "<i class='rf rf-savenewversion'></i> 保存新版本";
        saveNewVersionButton.classList.add('disabled');
    }

    /**
     * Save the crosstab file.
     * @param {boolean} isNewVersion - Whether to save as a new version
     */
    function saveFile(isNewVersion) {
        let xml = null;
        try {
            xml = crossTable.toXml();
        } catch (e) {
            bootbox.alert(e.message || e);
            return;
        }
        if (!xml) return;

        xml = encodeURIComponent(xml);
        const saveUrl = window._server + '/common/saveFile';

        if (isNewVersion) {
            bootbox.prompt('请输入新版本描述.', function (comment) {
                if (comment) {
                    ajaxSave(saveUrl, {
                        content: xml,
                        file: file,
                        newVersion: isNewVersion,
                        versionComment: comment
                    }, function () {
                        clearDirty();
                    });
                }
            });
        } else {
            ajaxSave(saveUrl, {
                content: xml,
                file: file,
                newVersion: isNewVersion
            }, function () {
                clearDirty();
            });
        }
    }

    // Load the crosstab data from server
    let loadUrl = window._server + '/common/loadXml';
    const doImport = getParameter('doImport');
    if (doImport && doImport.length > 1) {
        loadUrl += '?doImport=true';
    }

    fetch(loadUrl, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({files: file}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (response) {
        const data = response[0];

        // Convert cells array to a Map keyed by "rowNumber,columnNumber"
        data.cellsMap = function (tableData) {
            const map = new Map();
            const cells = tableData.cells;
            for (const cell of cells) {
                const key = cell.row + ',' + cell.col;
                map.set(key, cell);
            }
            return map;
        }(data);

        crossTable.init(data);

        // Load library references
        const libraries = data.libraries;
        if (libraries) {
            for (let i = 0; i < libraries.length; i++) {
                const lib = libraries[i];
                const type = lib.type;
                const path = lib.path;
                switch (type) {
                    case 'Constant':
                        constantLibraries.push(path);
                        break;
                    case 'Action':
                        actionLibraries.push(path);
                        break;
                    case 'Variable':
                        variableLibraries.push(path);
                        break;
                    case 'Parameter':
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
        clearDirty();
    }).catch(function (error) {
        document.body.innerHTML = '';
        if (error && error.status === 401) {
            bootbox.alert('权限不足，不能进行此操作.');
        } else if (error && error.text) {
            error.text().then(function(text) {
                try {
                    const result = JSON.parse(text);
                    bootbox.alert("<span style='color: red'>服务端错误：" + result.errorMsg + '</span>');
                } catch (e) {
                    bootbox.alert("<span style='color: red'>服务端错误：" + text + '</span>');
                }
            });
        } else {
            bootbox.alert("<span style='color: red'>服务端出错</span>");
        }
    });
});
