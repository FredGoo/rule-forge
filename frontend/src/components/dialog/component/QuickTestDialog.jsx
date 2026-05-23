import React, {Component} from 'react';
import ReactDOM from 'react-dom';
import CommonDialog from './CommonDialog.jsx';
import * as event from '../../componentEvent.js';
import * as action from '../../componentAction.js';
import CodeMirror from 'codemirror';
import '../../../../node_modules/codemirror/lib/codemirror.css';
import '../../../../node_modules/codemirror/theme/3024-day.css'; // 对应配置中的theme: "default"
// import '../../../../node_modules/codemirror/addon/hint/show-hint.js';
export default class QuickTestDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {
            title: '',
            versionsList: [], // 版本号下拉列表
            testRuleSets: [], // 测试规则集
            selectedVersion: '', // 选中的版本号
            // selectedRuleSet: '', // 选中的测试规则集
            orderNo: '', // 订单号
            variableData: [], // 数据源列表
            // decisionResult: '', // 决策结果
            creditScore: '', // 信用评分
            showLog: false, // 是否显示日志
            type: 'form', // 输入数据类型, form:表单,  json:json
            jsonInput: '{\n  \"key\": \"value\"\n}',
            fileType: ''
        };
    }

    componentDidMount() {
        this.editor = CodeMirror.fromTextArea(document.getElementById('json-editor'), {
            mode: "javascript",             // 设置语言模式
            lineNumbers: true,        // 显示行号
            theme: "3024-day",         // 设置主题
            autoCloseBrackets: true,  // 自动闭合括号
            matchBrackets: true,      // 匹配括号高亮
            foldGutter: true,         // 启用代码折叠
            gutters: ["CodeMirror-linenumbers", "CodeMirror-foldgutter"],
            indentUnit: 4,            // 缩进单位
            smartIndent: true         // 智能缩进
        });
        event.eventEmitter.on(event.OPEN_QUICK_TEST_DIALOG, (config) => {
            var file = config.file;
            
            action.loadFileVersions(file, function (data){
                this.setState({ 
                    project: config.project,
                    versionsList: data,
                    title: "对当前文件进行测试",
                    file,
                    fileType: config.type,
                    testRuleSets: [], // 测试规则集
                    selectedVersion: '', // 选中的版本号
                    // selectedRuleSet: '', // 选中的测试规则集
                    orderNo: '', // 订单号
                    variableData: [], // 数据源列表
                    // decisionResult: '', // 决策结果
                    creditScore: '', // 信用评分
                    type: 'form', 
                    resultData: []
                })
                $(ReactDOM.findDOMNode(this)).modal('show');
            }.bind(this));
        });
        event.eventEmitter.on(event.HIDE_QUICK_TEST_DIALOG, () => {
            $(ReactDOM.findDOMNode(this)).modal('hide');
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_QUICK_TEST_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_QUICK_TEST_DIALOG);
    }

    componentDidUpdate() {
        if (this.editor) {
            this.editor.refresh();
        }
    }
    render() {
        const body=(
            <div>
                <div style={{display: 'flex', justifyContent: 'space-between'}}>
                    <div className="form-group" style={{display: 'flex', alignItems: 'center'}}>
                        <label style={{fontSize: '15px', width: '120px'}}>输入数据</label>
                        <select value={this.state.type} className="form-control" onChange={(e)=>{
                            this.setState({ type: e.target.value });
                        }}>
                            <option value="form">表单</option>
                            <option value="json">json</option>
                        </select>
                    </div>
                    <div className="form-group" style={{display: 'flex', alignItems: 'center'}}>
                        <input type="text" 
                            className="form-control" 
                            name="packageName" 
                            style={{display: this.state.type === 'form' ? 'block' : 'none'}}
                            value={this.state.orderNo} placeholder='订单号'
                            onChange={(e) => this.setState({ orderNo: e.target.value })}/>
                        <button id="search" type="button" className="btn navbar-btn" style={{marginLeft: '10px', display: this.state.type === 'form' ? 'block' : 'none'}} onClick={e=>{
                            console.log('订单号', this.state)
                            if(this.state.type === 'json') {
                                return
                            }

                            if(!this.state.orderNo) {
                                alert('请输入订单号')
                                return
                            }
                            const params = {
                                projectId: this.state.project,
                                appId: this.state.orderNo,
                                filePath: `jcr:${this.state.file},${this.state.selectedVersion}`,
                                // ruleName: this.state.selectedRuleSet
                            }
                            action.loadVariableCategories(params, (data) => {
                                console.log('数据源', data)
                                this.setState({ 
                                    variableData: data
                                });
                                this.editor.setValue(JSON.stringify(data, null, 2));  // 添加格式化参数
                                this.editor.refresh();
                            });
                        }}>查询</button>
                    </div>
                </div>
                {/* form表单展示以下内容 */}
                {this.state.type === 'form' && (this.state.variableData || []).map((item, key) => (
                    <div>
                        <label>{item.name}</label>
                        <div style={{display: 'flex', flexWrap: 'wrap', paddingLeft: '10px'}}>
                            {((item.variables || []).map((ele, i)=>(
                                <div className="form-group" style={{marginLeft: '10px', display: 'flex', alignItems: 'center'}}>
                                    <label style={{minWidth: '80px',textAlign: 'right'}}>{ele.label}</label>
                                    <input type="text" className="form-control" style={{marginLeft: '10px'}} value={ele.defaultValue||''} onChange={e=>this.setState({
                                        variableData: this.state.variableData.map((item, key2) => {
                                            if(key === key2) {
                                                item.variables[i].defaultValue = e.target.value
                                            }
                                            return item
                                        })
                                    })}/>
                                </div>
                            )))}
                        </div>
                    </div>
                ))}
                {/* json展示以下内容 */}
                <div className="form-group" style={{height: '300px', marginTop: '10px', display: this.state.type === 'json' ? 'block' : 'none'}}>
                    <textarea id='json-editor'></textarea>
                </div>
                <div style={{display: 'flex', alignItems: 'center'}}>
                    <label style={{fontSize: '15px'}}>输出数据</label>
                    <button style={{
                        color: '#5bc0de', 
                        marginLeft: '10px', 
                        cursor: 'pointer', 
                        border: 0,
                        background: '#fff',
                        display: this.state.showLog ? 'block' : 'none'
                    }} onClick={e=>{
                        // 【${item.ruleSetName}-else】
                        const logContent = this.state.logData.map(item => `<p>》》》规则（RuleSet：${decodeURIComponent(item.fileName)}，${item.version}），已被添加到执行队列；</p>`);
                        console.log('logContent', logContent)
                        bootbox.alert({title: '日志', message: logContent.join('')})
                    }}>查看详细日志</button>
                </div>
                {/* <div className="form-group" style={{display: 'flex', alignItems: 'center',paddingLeft: '10px', marginTop: '15px'}}>
                    <label>决策结果</label>
                    <label style={{marginLeft: '10px', color: 'red'}}>{this.state.decisionResult}</label>
                </div> */}
                {(this.state.resultData || []).map((item, key) => (
                    <div>
                        <label>{item.name}</label>
                        <div style={{display: 'flex', flexWrap: 'wrap',}}>
                            {(item.variables||[]).map((ele) => (
                             <div key={key} className="form-group" style={{marginLeft: '10px', display: 'flex', alignItems: 'center'}}>
                                <label style={{minWidth: '80px',textAlign: 'right'}}>{ele.label}</label>
                                <input type="text" className="form-control" style={{marginLeft: '10px'}} readOnly value={ele.defaultValue||''} />
                            </div>
                            ))}
                        </div>
                    </div>
                ))}
            </div>
        );
        const htmlContent = (
            <div style={{display: 'flex', alignItems: 'center'}}>
                <div className="">
                    <select className="form-control" value={this.state.selectedVersion} onChange={(e)=>{
                        this.setState({ selectedVersion: e.target.value });
                        // if(this.state.fileType === 'ruleLib') { // 决策集，展示规则集
                        //     action.loadTestRuleSets(this.state.file, e.target.value, (data) => {
                        //         // 更新测试规则集
                        //         this.setState({ 
                        //             testRuleSets: data,
                        //             selectedRuleSet: ''
                        //         });
                        //     });
                        // } else {
                            const params = {
                                projectId: this.state.project,
                                appId: this.state.orderNo,
                                filePath: `jcr:${this.state.file},${e.target.value}`,
                                ruleName: ''
                            }
                            action.loadVariableCategories(params, (data) => {
                                console.log('数据源', data)
                                this.setState({ 
                                    variableData: data
                                });
                                this.editor.setValue(JSON.stringify(data, null, 2));  // 添加格式化参数
                                this.editor.refresh();
                            });
                        // }
                    }}>
                        <option value="">版本号</option>
                        {(this.state.versionsList || []).map(version => (
                            <option 
                                key={version.createDate} 
                                value={version.name}
                                // selected={version.name === currentVersion} // 新增选中逻辑
                            >
                                {version.name}
                            </option>
                        ))}
                    </select>
                </div>
                {/* <div className="" style={{marginLeft: '10px', display: this.state.fileType === 'ruleLib' ? 'block' : 'none'}}>
                    <select className="form-control" value={this.state.selectedRuleSet} 
                        onChange={(e)=>{
                            if(!e.target.value || !this.state.selectedVersion) return
                            this.setState({ selectedRuleSet: e.target.value });
                            // 获取数据源
                            const params = {
                                projectId: this.state.project,
                                appId: this.state.orderNo,
                                filePath: `jcr:${this.state.file},${this.state.selectedVersion}`,
                                ruleName: e.target.value
                            }
                            action.loadVariableCategories(params, (data) => {
                                console.log('数据源', data)
                                this.setState({ 
                                    variableData: data
                                });
                                this.editor.setValue(JSON.stringify(data, null, 2));  // 添加格式化参数
                                this.editor.refresh();
                            });
                        }}>
                        <option value="">测试规则集</option>
                        {(this.state.testRuleSets || []).map((ruleSet, i) => (
                            <option key={i} value={ruleSet.name}>{ruleSet.name}</option>
                        ))}
                    </select>
                </div> */}
                <div className="" style={{marginLeft: '10px'}}>
                    <button id="testButton" type="button" className="btn btn-success navbar-btn" onClick={e=>{
                        if(!this.state.selectedVersion) {
                            alert('请选择版本号')
                            return
                        }
                        // if(this.state.fileType === 'ruleLib' && !this.state.selectedRuleSet) {
                        //     alert('请选择测试规则集')
                        //     return
                        // }
                        console.log('变量数据源',this.state.variableData)
                        let logData = this.state.logData || []
                        logData.push({
                            project: this.state.project, // 项目名称
                            fileName: this.state.file,
                            // ruleSetName: this.state.selectedRuleSet, // 规则集名称
                            version: this.state.selectedVersion, // 版本号
                        })

                        let params = {
                            filePath: `jcr:${this.state.file},${this.state.selectedVersion}`,
                            // ruleName: this.state.selectedRuleSet,
                            data: this.state.variableData
                        }
                        if(this.state.type === 'json') {
                            try {
                                params.data = JSON.parse(this.editor.getValue());
                            } catch (error) {
                                bootbox.alert('JSON格式错误，请检查输入内容');
                                return;
                            }
                        }

                        action.beginTest(params,this.state.type, (data) => {
                            this.setState({
                                showLog: true,
                                // decisionResult: status ? '成功' : '失败',
                                resultData: data,
                                logData
                            })
                        });
                    }}>开始测试</button>
                </div>
            </div>
        )
        const buttons = [];
        
        return (
            <CommonDialog title={this.state.title} body={body} htmlContent={htmlContent} buttons={buttons} large={true}/>
        );
    }
}
