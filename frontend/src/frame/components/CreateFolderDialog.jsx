import React, {Component} from 'react';
import Dialog from '../../components/dialog/component/Dialog.jsx';
import * as componentEvent from '../../components/componentEvent.js';
import * as event from '../event.js';
import * as action from '../action.js';

const NAME_REGEXP = /^(?!_)(?!-)[一-龥_a-zA-Z0-9_-]{1,}$/;

export default class CreateFolderDialog extends Component {
    constructor(props) {
        super(props);
        this.state = {visible: false, newFolderName: '', errors: {}};
        this._validate = this._validate.bind(this);
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_CREATE_FOLDER_DIALOG, (data) => {
            this.setState({nodeData: data.nodeData, newFolderName: '', errors: {}});
            this.setState({visible: true});
        });
        event.eventEmitter.on(event.CLOSE_CREATE_FOLDER_DIALOG, () => {
            this.setState({visible: false});
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_CREATE_FOLDER_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_CREATE_FOLDER_DIALOG)
    }

    async _validate() {
        const value = this.state.newFolderName;
        const errors = {};
        if (!value || !value.trim()) {
            errors.newFolderName = '目录名不能为空';
        } else if (!NAME_REGEXP.test(value)) {
            errors.newFolderName = '名称只能包含中文及英文字母、数字、下划线、中划线,且不能以下划线、中划线开头';
        } else {
            const fullFileName = this.state.nodeData.fullPath + '/' + value;
            const resp = await fetch(window._server + '/frame/fileExistCheck', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'fullFileName=' + encodeURIComponent(fullFileName)
            });
            const result = await resp.json();
            if (result === false || (typeof result === 'object' && result.valid === false)) {
                errors.newFolderName = '目录已存在';
            }
        }
        return {valid: Object.keys(errors).length === 0, errors};
    }

    render() {
        const dispatch = this.props.dispatch;
        const body = (
            <div className="form-group">
                <label>新目录名称</label>
                <input type="text" className="form-control" name="newFolderName" value={this.state.newFolderName}
                       onChange={function(e){this.setState({newFolderName: e.target.value, errors: {}})}.bind(this)}></input>
                {this.state.errors.newFolderName && <div className="text-danger" style={{fontSize: '12px'}}>{this.state.errors.newFolderName}</div>}
            </div>
        );
        const buttons = [];
        buttons.push(
            {
                name: '保存',
                className: 'btn btn-success',
                icon: 'fa fa-floppy-o',
                click: async function () {
                    const {valid, errors} = await this._validate();
                    if (!valid) {
                        this.setState({errors});
                        return;
                    }
                    componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                    const newFolderName = this.state.newFolderName;
                    const nodeData = this.state.nodeData;
                    setTimeout(function () {
                        dispatch(action.createNewFolder(newFolderName, nodeData));
                    }.bind(this), 200);
                }.bind(this)
            }
        );
        return (
            <Dialog visible={this.state.visible} title="创建新目录" body={body} buttons={buttons}
                    onClose={() => this.setState({visible: false})}></Dialog>
        );
    }
}
