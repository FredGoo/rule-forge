import React, {Component} from 'react';
import ReactDOM from 'react-dom';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';

export default class BatchTestDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {data: null, files: null, slaveData: null, filters: []};
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_BATCH_TEST_DIALOG, (config) => {
            $(ReactDOM.findDOMNode(this)).modal({
                show: true,
                backdrop: 'static',
                keyboard: false
            });
            this.setState({data: config.data, files: config.files});
        });
        event.eventEmitter.on(event.HIDE_BATCH_TEST_DIALOG, () => {
            $(ReactDOM.findDOMNode(this)).modal('hide');
        });
    }

    render() {
        const formId = 'batch_test_excel_form';
        let body = (<div>
            <div id="div-flow"></div>
        </div>);

        const buttons = [
            {
                name: '测试',
                className: 'btn btn-danger',
                icon: 'glyphicon glyphicon-flash',
                click: function () {
                    action.doBatchTest(this.files, function (res) {
                        const containerId = 'div-flow';

                        // $('#' + containerId).html('');
                        // const designer = new RuleFlowDesigner(containerId);
                        // designer.fromJson(res);
                        // designer.buildDesigner();
                        // $('.fd-toolbar').hide();
                        // $('.fd-property-panel').hide();
                        // Event.eventEmitter.emit(Event.CANVAS_SELECTED);
                    });
                }
            }
        ];
        return (<CommonDialog large={true} title='对导入的Excel数据进行批量测试' body={body} buttons={buttons}/>);
    }
};