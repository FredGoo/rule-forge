import React, {Component} from 'react';
import ReactDOM from 'react-dom';
import * as event from '../event.js';
import * as action from '../action.js';
import Grid from '../../components/grid/component/Grid.jsx';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import RuleFlowDesigner from "../../flow/RuleFlowDesigner";
import StartTool from "../../flow/StartTool";
import RuleTool from "../../flow/RuleTool";
import PackageTool from "../../flow/PackageTool";
import ActionTool from "../../flow/ActionTool";
import ScriptTool from "../../flow/ScriptTool";
import DecisionTool from "../../flow/DecisionTool";
import ForkTool from "../../flow/ForkTool";
import JoinTool from "../../flow/JoinTool";
import RulesPackageTool from "../../flow/RulesPackageTool";

function downloadSvg(div, prefix) {
    var svgXml = $(div).html();

    var image = new Image();
    // 给图片对象写入base64编码的svg流
    image.src = 'data:image/svg+xml;base64,' + window.btoa(unescape(encodeURIComponent(svgXml)));

    image.onload = function () {
        // 准备空画布
        var canvas = document.createElement('canvas');
        canvas.width = $(div + ' svg').width();
        canvas.height = $(div + ' svg').height();

        // 取得画布的2d绘图上下文
        var context = canvas.getContext('2d');
        context.fillStyle = '#fff';
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.drawImage(image, 0, 0);

        var a = document.createElement('a');
        // 将画布内的信息导出为png图片数据
        a.href = canvas.toDataURL('image/png');
        // 设定下载名称
        a.download = prefix + "flowpic";
        // 点击触发下载
        a.click();
    }
}

export default class FlowDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {flows: null, project: '', packageId: ''};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_FLOW_DIALOG, (config) => {
            this.setState({
                project: config.project,
                packageId: config.packageId,
            });

            this.rowData = null;
            $(ReactDOM.findDOMNode(this)).modal('show');
            var files = config.files;
            var data = config.data;
            action.loadFlows(files, function (result) {
                this.setState({files, data, flows: result});
                const ce = window.parent.componentEvent;
                ce.eventEmitter.emit(ce.HIDE_LOADING);
            }.bind(this));
        });
        event.eventEmitter.on(event.HIDE_FLOW_DIALOG, () => {
            $(ReactDOM.findDOMNode(this)).modal('hide');
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_FLOW_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_FLOW_DIALOG);
    }

    render() {
        const containerId = 'div-flow';
        const formId = 'export-test-excel-form';

        const headers = [
            {id: 'f-id', name: 'id', label: '决策流ID', filterable: true},
        ];
        const {files, data, project, packageId} = this.state;
        const gridOperationCol = {
            width: '70px',
            operations: [
                {
                    label: '测试',
                    icon: 'glyphicon glyphicon-flash',
                    style: {fontSize: '20px', color: '#d9534f', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex, rowData) {
                        const ce = window.parent.componentEvent;
                        ce.eventEmitter.emit(ce.SHOW_LOADING);
                        var flowId = rowData.id;
                        action.testFlow({
                            'project': project,
                            'packageId': packageId,
                            'files': files,
                            'data': [data],
                            'flowId': flowId
                        }, function (result) {
                            event.eventEmitter.emit(event.REFRESH_SIMULATOR_DATA, result);
                            ce.eventEmitter.emit(ce.HIDE_LOADING);
                            bootbox.alert("决策流[" + flowId + "]执行完成，" + result.info);
                        });
                    }
                },
                {
                    label: '批量测试',
                    icon: 'glyphicon glyphicon-send',
                    style: {fontSize: '20px', color: '#d9534f', padding: '0px 4px', cursor: 'pointer'},
                    click: function (rowIndex, rowData) {
                        const ce = window.parent.componentEvent;
                        ce.eventEmitter.emit(ce.SHOW_LOADING);
                        const flowId = rowData.id;
                        action.doBatchTest({
                            'project': project,
                            'packageId': packageId,
                            'files': files,
                            'flowId': flowId
                        }, function (testResult) {
                            ce.eventEmitter.emit(ce.HIDE_LOADING);
                            // bootbox.alert("决策流[" + flowId + "]执行完成，" + result.info);
                            const errorList = testResult['errorList']
                            bootbox.alert(JSON.stringify(errorList));

                            // 展示流程图
                            fetch(window._server + '/ruleflowdesigner/loadFlowDefinition', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                body: new URLSearchParams({file: files.split(':')[1].split(',')[0]}).toString()
                            }).then(function(response) {
                                if (!response.ok) throw response;
                                return response.json();
                            }).then(function (flowJson) {
                                $('#' + containerId).html('');

                                const designer = new RuleFlowDesigner(containerId);
                                delete flowJson.libraries;
                                designer.addTool(new StartTool());
                                designer.addTool(new RuleTool());
                                designer.addTool(new PackageTool());
                                designer.addTool(new ActionTool());
                                designer.addTool(new ScriptTool());
                                designer.addTool(new DecisionTool());
                                designer.addTool(new ForkTool());
                                designer.addTool(new JoinTool());
                                designer.addTool(new RulesPackageTool());
                                designer.buildDesigner();

                                $('.fd-toolbar').hide();
                                $('.fd-property-panel').remove();
                                $('.fd-node-toolbar').hide();

                                for (const index in flowJson.nodes) {
                                    const num = testResult['flowMap'][flowJson.nodes[index].name];
                                    if (num != null) {
                                        flowJson.nodes[index].text = flowJson.nodes[index].name + ' (' + num + ')';
                                    }
                                }
                                designer.fromJson(flowJson);

                                const datetime = new Date();
                                const filePrefix = '' + datetime.getFullYear() + (datetime.getMonth() + 1) + datetime.getDate()
                                    + datetime.getHours() + datetime.getMinutes() + datetime.getSeconds() + '_';
                                // 下载excel
                                document.getElementById("input-prefix").value = filePrefix;
                                document.getElementById(formId).submit();
                                // 下载图片
                                downloadSvg('.fd-canvas-container', filePrefix);
                            }).catch(function () {
                                alert(`加载决策流${files}失败！`);
                            });
                        });
                    }
                }
            ]
        };
        let body = (<div></div>);
        if (this.state.flows) {
            body = (
                <div>
                    <Grid headers={headers} operationConfig={gridOperationCol} rows={this.state.flows}/>
                    <div id={containerId}></div>
                    <form id={formId} method="post"
                          action={window._server + '/packageeditor/exportBatchTestExcel'}>
                        <input id="input-prefix" name="prefix" type="hidden"/>
                    </form>
                </div>
            );
        }
        const buttons = [
            {
                name: '关闭',
                className: 'btn btn-primary',
                icon: 'fa fa-close',
                click: function () {
                    event.eventEmitter.emit(event.HIDE_FLOW_DIALOG);
                }
            }
        ];

        return (
            <CommonDialog title='测试决策流' body={body} buttons={buttons}/>
        );
    }
}