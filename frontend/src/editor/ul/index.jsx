import '../../bootbox.js';
import '../../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../../../node_modules/codemirror/lib/codemirror.css';
import '../../../node_modules/codemirror/addon/hint/show-hint.css';
import '../../../node_modules/codemirror/addon/lint/lint.css';
import './ul.css';
import React from 'react';
import { createRoot } from 'react-dom/client';
import {MsgBox} from 'flowdesigner';
import CodeMirror from 'codemirror';
import '../../../node_modules/codemirror/addon/mode/simple.js';
import '../../../node_modules/codemirror/addon/hint/show-hint.js';
import '../../../node_modules/codemirror/addon/lint/lint.js';
import './ruleforge_mode.js';
import './ruleforge-hint.js';
import './ruleforgemixed.js';
import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import QuickTestDialog from '../../components/dialog/component/QuickTestDialog.jsx';
import {ajaxSave, buildProjectNameFromFile, getParameter, saveNewVersion} from '../../Utils.js';
import * as event from '../../components/componentEvent.js';

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    window._project = buildProjectNameFromFile(file);

    createRoot(document.getElementById("dialogContainer")).render(
        <div>
            <KnowledgeTreeDialog/>,
            <QuickTestDialog/>
        </div>,
);

    CodeMirror.commands.autocomplete = function (cm) {
        cm.showHint({hint: CodeMirror.hint.ruleforge});
    };
    var codeEditor = document.getElementById("codeEditor");
    window.codeMirror = CodeMirror.fromTextArea(codeEditor, {
        lineNumbers: true,
        mode: "rulemixed",
        extraKeys: {"Alt-/": "autocomplete"},
        gutters: ["CodeMirror-linenumbers", "CodeMirror-lint-markers"],
        lint: {
            getAnnotations: buildScriptLintFunction('Script'),
            async: true
        }
    });
    codeMirror.on("change", function (cm, e) {
        var value = cm.getValue();
        if (e.text == ".") {
            CodeMirror.commands.autocomplete(codeMirror);
        }
    });
    init();
});

function buildScriptLintFunction(type) {
    return function (text, updateLinting, options, editor) {
        if (text === '') {
            updateLinting(editor, []);
            return;
        }
        const url = window._server + '/common/scriptValidation';
        fetch(url, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({type, content: text}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then(function (result) {
            if (result) {
                for (let item of result) {
                    item.from = {line: item.line - 1};
                    item.to = {line: item.line - 1};
                }
                updateLinting(editor, result);
            } else {
                updateLinting(editor, []);
            }
        }).catch(function (response) {
            if (response && response.status === 401) {
                bootbox.alert("权限不足，不能进行此操作.");
            } else if (response && response.text) {
                response.text().then(function(text) {
                    bootbox.alert("<span style='color: red'>语法检查操作失败：" + text + "</span>");
                });
            } else {
                bootbox.alert("<span style='color: red'>语法检查操作失败,服务端出错</span>");
            }
        });
    };
}

function init() {
    var height = document.documentElement.scrollHeight - 60;
    codeMirror.setSize("100%", height);
    window._dirty = false;
    var file = getParameter("file");
    if (!file || file.length < 1) {
        alert("当前编辑器未指定具体文件！");
        return;
    }
    var saveButton = `<div class="btn-group btn-group-sm navbar-btn" style="margin:1px;" role="group" aria-label="...">
            <button id="saveButton" type="button" class="btn btn-default navbar-btn" ><i class="rf rf-save"></i> 保存</button>
            <button id="saveButtonNewVersion" type="button" class="btn btn-default navbar-btn"><i class="rf rf-savenewversion"></i> 生成版本</button>
     </div>`;
    var testButton = '<div class="btn-group btn-group-sm navbar-btn" style="margin-left:5px;margin-top:0;margin-bottom: 0" role="group" aria-label="...">' +
        '<button id="testButton" type="button" class="btn btn-success navbar-btn"><i class="glyphicon glyphicon-flash"/> 快速测试</button>' +
        '</div>';
    var toolbarContainer = document.getElementById("toolbarContainer");
    var nav = document.createElement('nav');
    nav.className = 'navbar navbar-default';
    nav.style.marginBottom = '1px';
    nav.innerHTML = `<div>
        <div class="collapse navbar-collapse"> ${saveButton}
                <div class="btn-group btn-group-sm navbar-btn" style="margin:1px;" role="group" aria-label="...">
                    <button id="addVarButton" type="button" class="btn btn-default"><i class="rf rf-variable"></i> 变量库</button>
                    <button id="addConstantsButton" type="button" class="btn btn-default"><i class="rf rf-constant"></i> 常量库</button>
                    <button id="addActionButton" type="button" class="btn btn-default"><i class="rf rf-action"></i> 动作库</button>
                    <button id="configParameterButton" type="button" class="btn btn-default"><i class="rf rf-parameter"></i> 参数库</button>
                </div>
                ${testButton}
             </div>
        </div>`;
    toolbarContainer.appendChild(nav);
    document.getElementById("saveButton").addEventListener('click', function () {
        save(file, false);
    });
    document.getElementById("saveButtonNewVersion").addEventListener('click', function () {
        save(file, true);
    });
    document.getElementById("testButton").addEventListener('click', function() {
        let decodedFile = decodeURIComponent(file)
        event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile});
    });

    document.getElementById("configParameterButton").addEventListener('click', function () {
        event.eventEmitter.emit(event.OPEN_KNOWLEDGE_TREE_DIALOG, {
            project: window._project,
            fileType: 'ParameterLibrary',
            callback: function (file, version) {
                let path = 'jcr:' + file;
                if (version !== 'LATEST') {
                    path += ':' + version;
                }
                selectResource('ParameterLibrary', path);
            }
        });
    });
    document.getElementById("addVarButton").addEventListener('click', function () {
        event.eventEmitter.emit(event.OPEN_KNOWLEDGE_TREE_DIALOG, {
            project: window._project,
            fileType: 'VariableLibrary',
            callback: function (file, version) {
                let path = 'jcr:' + file;
                if (version !== 'LATEST') {
                    path += ':' + version;
                }
                selectResource('VariableLibrary', path);
            }
        });
    });
    document.getElementById("addConstantsButton").addEventListener('click', function () {
        event.eventEmitter.emit(event.OPEN_KNOWLEDGE_TREE_DIALOG, {
            project: window._project,
            fileType: 'ConstantLibrary',
            callback: function (file, version) {
                let path = 'jcr:' + file;
                if (version !== 'LATEST') {
                    path += ':' + version;
                }
                selectResource('ConstantLibrary', path);
            }
        });
    });
    document.getElementById("addActionButton").addEventListener('click', function () {
        event.eventEmitter.emit(event.OPEN_KNOWLEDGE_TREE_DIALOG, {
            project: window._project,
            fileType: 'ActionLibrary',
            callback: function (file, version) {
                let path = 'jcr:' + file;
                if (version !== 'LATEST') {
                    path += ':' + version;
                }
                selectResource('ActionLibrary', path);
            }
        });
    });

    window._dirty = false;
    var url = window._server + '/uleditor/loadUL';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({file}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        codeMirror.setValue(data);
        document.getElementById("saveButton").classList.add("disabled");
        codeMirror.on("change", function () {
            setDirty();
        });
        loadResLib();
    }).catch(function (response) {
        if (response && response.status === 401) {
            bootbox.alert("权限不足，不能进行此操作.");
        } else if (response && response.text) {
            response.text().then(function(text) {
                bootbox.alert("<span style='color: red'>文件加载失败：" + text + "</span>");
            });
        } else {
            bootbox.alert("<span style='color: red'>文件加载失败,服务端出错</span>");
        }
    });
};

function selectResource(type, res) {
    codeMirror.replaceSelection("import" + type + " \"" + res + "\";");
    loadResLib();
};

function loadResLib() {
    var file = getParameter("file");
    var content = codeMirror.getValue();
    if (!content || content.length < 10) {
        MsgBox.alert("请先输入脚本.");
        return;
    }
    var url = window._server + '/uleditor/loadULLibs';
    fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({content: content}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (data) {
        codeMirror._library = data;
    }).catch(function (response) {
        if (response && response.status === 401) {
            bootbox.alert("权限不足，不能进行此操作.");
        } else if (response && response.text) {
            response.text().then(function(text) {
                bootbox.alert("<span style='color: red'>资源库加载失败：" + text + "</span>");
            });
        } else {
            bootbox.alert("<span style='color: red'>资源库加载失败,服务端出错</span>");
        }
    });
};

function save(file, newVersion) {
    if (!newVersion && document.getElementById("saveButton").classList.contains("disabled")) {
        return false;
    }
    var content = codeMirror.getValue();
    content = encodeURIComponent(content);
    let postData = {content, file, newVersion};
    const url = window._server + '/common/saveFile';
    if (newVersion) {
        saveNewVersion(url, postData, function () {
            cancelDirty();
            bootbox.alert('保存成功!');
        });
    } else {
        ajaxSave(url, postData, function () {
            cancelDirty();
        })
    }
};

function setDirty() {
    if (window._dirty) {
        return;
    }
    window._dirty = true;
    document.getElementById("saveButton").innerHTML = "<i class='rf rf-save'></i> *保存";
    document.getElementById("saveButton").classList.remove("disabled");
};

function cancelDirty() {
    if (!window._dirty) {
        return;
    }
    window._dirty = false;
    document.getElementById("saveButton").innerHTML = "<i class='rf rf-save'></i> 保存";
    document.getElementById("saveButton").classList.add("disabled");
};
