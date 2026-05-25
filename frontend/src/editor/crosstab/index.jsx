import '../../bootbox.js';

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
import {createRoot} from 'react-dom/client';
import ResourceVersionDialogComponent from '../common/ResourceVersionDialogComponent.jsx';
import ResourceListDialogComponent from '../common/ResourceListDialogComponent.jsx';
import ConfigLibraryDialog from '../../components/dialog/component/ConfigLibraryDialog.jsx';
import EditorToolbar from '../../components/editor-toolbar/EditorToolbar.jsx';

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

    let toolbarApi = null;

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
                        toolbarApi.clearDirty();
                    });
                }
            });
        } else {
            ajaxSave(saveUrl, {
                content: xml,
                file: file,
                newVersion: isNewVersion
            }, function () {
                toolbarApi.clearDirty();
            });
        }
    }

    createRoot(document.getElementById('toolbarContainer')).render(
        <EditorToolbar
            onSave={saveFile}
            onReady={(api) => { toolbarApi = api; }}
            extraButtons={[
                <button key="excel" type="button" className="btn btn-default" style={{height: '36px'}}
                        onClick={() => new ExcelImportDialog().show()}>
                    <i className="glyphicon glyphicon-share-alt" style={{fontSize: '16px'}}></i> 导入Excel
                </button>
            ]}
        />
    );

    createRoot(document.getElementById('dialogContainer')).render(
        <div>
            <ResourceVersionDialogComponent/>
            <ResourceListDialogComponent/>
            <ConfigLibraryDialog/>
        </div>
    );

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
        toolbarApi.clearDirty();
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
