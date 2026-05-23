import '../css/iconfont.css';
import '../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../../node_modules/codemirror/lib/codemirror.css';
import '../../node_modules/codemirror/addon/hint/show-hint.css';
import '../../node_modules/codemirror/addon/lint/lint.css';
import './flow.css';
import RuleFlowDesigner from './RuleFlowDesigner.js';
import StartTool from './StartTool.js';
import RuleTool from './RuleTool.js';
import PackageTool from './PackageTool.js';
import ActionTool from './ActionTool.js';
import ScriptTool from './ScriptTool.js';
import DecisionTool from './DecisionTool.js';
import ForkTool from './ForkTool.js';
import JoinTool from './JoinTool.js';
import RulesPackageTool from './RulesPackageTool.js';
import React from 'react';
import { createRoot } from 'react-dom/client';
import {Event} from 'flowdesigner';
import KnowledgeTreeDialog from '../components/dialog/component/KnowledgeTreeDialog.jsx';
import QuickTestDialog from '../components/dialog/component/QuickTestDialog.jsx';
import * as event from '../components/componentEvent.js';
import {buildProjectNameFromFile, getParameter} from '../Utils.js';
import {saveNewVersion, ajaxSave} from "../Utils";

$(document).ready(function () {
    const containerId = 'container';
    const designer = new RuleFlowDesigner(containerId);
    const file = getParameter('file');
    window._project = buildProjectNameFromFile(file);

    designer.addButton({
        icon: '<i class="rf rf-savenewversion"/>',
        tip: '生成版本',
        click: function () {
            event.eventEmitter.emit(event.SHOW_LOADING, "数据保存中");
            const content = designer.toXML();
            if (!content) {
                event.eventEmitter.emit(event.HIDE_LOADING);
                return;
            }
    
            let postData = {content, file, newVersion: true};
            const url = window._server + '/common/saveFile';
            saveNewVersion(url, postData, function () {
                event.eventEmitter.emit(event.HIDE_LOADING);
                bootbox.alert("保存成功");
            });
        }
    });
    designer.addButton({
        icon: '<i class="rf rf-save"/>',
        tip: '保存',
        click: function () {
            event.eventEmitter.emit(event.SHOW_LOADING, "数据保存中");
            const content = designer.toXML();
            if (!content) {
                event.eventEmitter.emit(event.HIDE_LOADING);
                return;
            }

            let postData = {content, file, newVersion: false};
            const url = window._server + '/common/saveFile';
            ajaxSave(url, postData, function () {
                event.eventEmitter.emit(event.HIDE_LOADING);
                bootbox.alert("保存成功");
            });
        }
    });
    designer.addTool(new StartTool());
    designer.addTool(new RuleTool());
    designer.addTool(new PackageTool());
    designer.addTool(new ActionTool());
    designer.addTool(new ScriptTool());
    designer.addTool(new DecisionTool());
    designer.addTool(new ForkTool());
    designer.addTool(new JoinTool());
    designer.addTool(new RulesPackageTool()); 
    // designer.addButton({
    //     icon: '<i class="glyphicon glyphicon-flash"/>',
    //     tip: '快速测试',
    //     click: function () {
    //         let decodedFile = decodeURIComponent(file)
    //         event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile});
    //     }
    // });
    designer.buildDesigner();

    // 确保画布容器有正确的高度
    $('.fd-canvas-container').css('height', $(window).height() - 100);

    // 监听窗口大小变化，确保画布高度正确
    $(window).resize(function() {
        $('.fd-canvas-container').css('height', $(window).height() -10);
        // 重新刷新所有节点位置
        if (designer.context && designer.context.allFigures) {
            designer.context.allFigures.forEach(figure => {
                if (figure._moveAndResizeTextAndIcon) {
                    figure._moveAndResizeTextAndIcon();
                }
            });
        }
    });

    const container = $('#' + containerId);
    container.append('<div id="__dialog_container"></div>');
    createRoot(document.getElementById("__dialog_container")).render(
        <div>
            <KnowledgeTreeDialog/>,
            <QuickTestDialog/>
        </div>,
);

    fetch(window._server + '/ruleflowdesigner/loadFlowDefinition', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({file}).toString()
    }).then(function(response) {
        if (!response.ok) throw response;
        return response.json();
    }).then(function (json) {
        // 确保画布尺寸已确定
        setTimeout(() => {
            designer.fromJson(json);
            Event.eventEmitter.emit(Event.CANVAS_SELECTED);
        }, 0);
    }).catch(function () {
        alert(`加载决策流${file}失败！`);
    });
});