import React, {Component} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';

export default class ExportExcelDataDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {visible: false, files: ''};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_EXPORT_EXCEL_DIALOG, (files) => {
            this.setState({visible: true, files});
        });
        event.eventEmitter.on(event.HIDE_EXPORT_EXCEL_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    comonentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_EXPORT_EXCEL_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_EXPORT_EXCEL_DIALOG);
    }

    render() {
        const formId = 'export_excel_form';
        const body = (
            <div>
                <form id={formId} method="post" action={window._server + '/packageeditor/exportExcelData'}>
                    <div>
                        <div className="form-group">
                            <label>开始时间:</label>
                            <input type="date" className="form-control" name="startTime" autoComplete="off"/>
                        </div>
                        <div className="form-group">
                            <label>结束时间:</label>
                            <input type="date" className="form-control" name="endTime" autoComplete="off"/>
                        </div>
                        <div className="form-group">
                            <label>项目名:</label>
                            <input type="text" className="form-control" name="projectName"
                                   autoComplete="off"/>
                        </div>
                        <div className="form-group">
                            <label>包名:</label>
                            <input type="text" className="form-control" name="packageName"
                                   autoComplete="off"/>
                        </div>
                    </div>
                </form>
            </div>
        );
        const buttons = [
            {
                name: '导出',
                className: 'btn btn-primary',
                icon: 'glyphicon glyphicon-cloud-download',
                click: function () {
                    document.getElementById(formId).submit();
                }
            }
        ];
        return (<CommonDialog visible={this.state.visible} title="导出Excel" body={body} buttons={buttons} onClose={() => this.setState({visible: false})}/>);
    }
}