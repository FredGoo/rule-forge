import React, {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';
import {seeFileVersions} from '../action.js';
import * as componentEvent from '../../components/componentEvent.js';
import {formatDate} from '../../Utils.js';

export default class VersionListDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {title: '', list: [], num: 0, visible: false};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_FILE_VERSION_DIALOG, config => {
            const {files, data, num} = config, file = data.fullPath;
            this.setState({title: `${file}文件版本列表`, list: files, num: num, data, visible: true});
        });
        event.eventEmitter.on(event.CLOSE_FILE_VERSION_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_FILE_VERSION_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_FILE_VERSION_DIALOG);
    }

    render() {
        const {list, data} = this.state;
        const body = (
            <div>
                <table className="table table-bordered table-hover"
                       style={{tableLayout: 'fixed', wordBreak: 'break-all'}}>
                    <thead>
                    <tr>
                        <td style={{width: '80px'}}>版本号</td>
                        {/*<td>版本描述</td>*/}
                        {/*<td>修改前</td>*/}
                        <td>修改后</td>
                        <td style={{width: '80px'}}>审批状态</td>
                        <td style={{width: '120px'}}>测试审批状态</td>
                        <td style={{width: '100px'}}>创建人</td>
                        <td style={{width: '160px'}}>创建时间</td>
                        <td style={{width: '100px'}}>操作</td>
                    </tr>
                    </thead>
                    <tbody>
                    {list.map(function (row, index) {
                        let auditStatusStr = "";
                        switch (+row.auditStatus) {
                            case 0:
                                auditStatusStr = "草稿";
                                break;
                            case 10:
                                auditStatusStr = "测试中";
                                break;
                            case 20:
                                auditStatusStr = "审批中";
                                break;
                            case 90:
                                auditStatusStr = "通过";
                                break;
                            case 91:
                                auditStatusStr = "拒绝";
                                break;
                        }
                        let testAuditStatus = '';
                        if(row.testAuditStatus !== null) {
                            switch (+row.testAuditStatus) {
                                case 0:
                                    testAuditStatus = "草稿";
                                    break;
                                case 10:
                                    testAuditStatus = "测试中";
                                    break;
                                case 20:
                                    testAuditStatus = "审批中";
                                    break;
                                case 90:
                                    testAuditStatus = "通过";
                                    break;
                                case 91:
                                    testAuditStatus = "拒绝";
                                    break;
                            }
                        }
                        return (
                            <tr key={index}>
                                <td>{row.name}</td>
                                {/*<td>{row.comment}</td>*/}
                                {/*<td>{row.beforeComment}</td>*/}
                                <td><pre>{row.afterComment}</pre></td>
                                <td>{auditStatusStr}</td>
                                <td>{testAuditStatus}</td>
                                <td>{row.createUser}</td>
                                <td>{formatDate(row.createDate, 'yyyy-MM-dd HH:mm:ss')}</td>
                                <td>
                                    <button type="button" className="btn btn-link" style={{padding: '0'}}
                                            onClick={() => {
                                                let url = '.' + data.editorPath + "?file=" + data.fullPath + ':' + row.name;
                                                let fullPath = data.fullPath + ':' + row.name;
                                                let name = data.name + ':' + row.name;
                                                if (data.type === 'resourcePackage') {
                                                    const packageName = data.fullPath.split("/")[1];
                                                    url = '.' + data.editorPath + "?file=" + packageName + '.rp:' + row.name;
                                                    fullPath = '/' + packageName + ':' + row.name;
                                                    name = data.name;
                                                }

                                                const config = {
                                                    id: data.id + ':' + row.name,
                                                    name: name,
                                                    fullPath: fullPath,
                                                    path: url,
                                                    active: true
                                                };
                                                componentEvent.eventEmitter.emit(componentEvent.TREE_NODE_CLICK, config);
                                            }}>打开
                                    </button>
                                    <button type="button" className="btn btn-link"
                                            style={{padding: '0', marginLeft: '8px'}}
                                            onClick={() => {
                                                const fullPath = data.fullPath + ':' + row.name;
                                                action.seeFileSource({fullPath});
                                            }}>源码
                                    </button>
                                </td>
                            </tr>
                        );
                    })}
                    </tbody>
                </table>

                <nav aria-label="分页">
                    <ul className="pagination">
                        <li>
                            <a href="#" aria-label="上一页"
                               onClick={() => {
                                   let queryData = data;
                                   queryData['page'] = data['page'] - 1;
                                   if (queryData['page'] < 1) {
                                       queryData['page'] = 1;
                                   }
                                   seeFileVersions(queryData);
                               }}>
                                <span aria-hidden="true">&laquo;</span>
                            </a>
                        </li>
                        {/*<li><a href="#">1</a></li>*/}
                        <li>
                            <a href="#" aria-label="下一页"
                               onClick={() => {
                                   let queryData = data;
                                   queryData['page'] = data['page'] + 1;
                                   seeFileVersions(queryData);
                               }}>
                                <span aria-hidden="true">&raquo;</span>
                            </a>
                        </li>
                    </ul>
                </nav>
            </div>
        );
        return (<CommonDialog visible={this.state.visible} body={body} title={this.state.title} buttons={[]} large={true} dialogStyle={{  // 直接设置样式对象
            minWidth: '700px'
        }}/>);
    }
}
