/**
 * Created by jacky on 2016/6/24.
 */
import React, {Component} from 'react';
import ReactDOM from 'react-dom';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';

export default class ImportExcelErrorDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {data: []};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_IMPORT_EXCEL_ERROR_DIALOG, (data) => {
            console.log('失败结果列表',data)
            $(ReactDOM.findDOMNode(this)).modal('show');
            this.setState({data});
        });
        event.eventEmitter.on(event.HIDE_IMPORT_EXCEL_ERROR_DIALOG, () => {
            $(ReactDOM.findDOMNode(this)).modal('hide');
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_IMPORT_EXCEL_ERROR_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_IMPORT_EXCEL_ERROR_DIALOG);
    }

    render() {
        const body = (
            <div>
                {this.state.data.slice(0, 10).map((item, index) => {
                    return <div style={{color: 'red'}} key={index}>{index+1}：{item.sheetName} 第{item.sheetRowId+1}行，{item.sheetFieldName} {item.errorMsg}</div>
                })}
                {this.state.data.length > 10 && <div style={{color: 'red'}}>......共{this.state.data.length}条，仅显示前10条，其他请下载详情查看</div>}
                <form id="formId" method="post"
                    action={window._server + '/packageeditor/exportBatchTestExcel'}>
                    <input id="input-prefix" name="prefix" type="hidden"/>
                </form>
            </div>
        );
        const _this = this
        const buttons = [
            {
                name: '下载',
                className: 'btn btn-primary',
                click: function () {
                    const datetime = new Date();
                    const filePrefix = '' + datetime.getFullYear() + (datetime.getMonth() + 1) + datetime.getDate()
                            + datetime.getHours() + datetime.getMinutes() + datetime.getSeconds() + '_';
                    // 下载excel
                    document.getElementById("input-prefix").value = filePrefix;
                    document.getElementById("formId").submit();
                }
            },
            {
                name: 'OK',
                className: 'btn btn-primary',
                click: function () {
                    $(ReactDOM.findDOMNode(_this)).modal('hide');
                }
            }
        ];
        return (<CommonDialog title="导入Excel失败" body={body} buttons={buttons}/>);

    }
}