import React,{Component,PropTypes} from 'react';
import ReactDOM from 'react-dom';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';

export default class AddParamsDialog extends Component{
    constructor(props){
        super(props);
        this.state={title:''};
    }
    componentDidMount(){
        console.log($(ReactDOM.findDOMNode(this)))
        $(ReactDOM.findDOMNode(this)).bootstrapValidator({
            feedbackIcons: {
                invalid: 'glyphicon glyphicon-remove',
                validating: 'glyphicon glyphicon-refresh'
            },
            fields:{
                name:{
                    validators: {
                        notEmpty: {
                            message: '字段名称不能为空'
                        },
                        regexp: {
                            regexp: /^[A-Za-z]+$/,
                            message: '字段名称只能英文输入'
                        }
                    }
                },
                label:{
                    validators: {
                        notEmpty: {
                            message: '标题不能为空'
                        },
                        regexp: {
                            regexp: /^[\u4e00-\u9fa5]+$/,
                            message: '标题必须使用中文'
                        }
                    }
                },
                defaultValue:{
                    validators: {
                        notEmpty: {
                            message: '默认值不能为空'
                        }
                    }
                },
                type:{
                    validators: {
                        notEmpty: {
                            message: '数据类型不能为空'
                        }
                    }
                }
            }
        });
        event.eventEmitter.on(event.OPEN_CREATE_PARAMS_DIALOG,(data)=>{
            $(ReactDOM.findDOMNode(this)).modal('show');
            const create=data.create;
            const title=data.title;
            const rowIndex=data.rowIndex;
            const masterRowData=data.data;
            if(create){
                $('[name=name]').val('');
                $('[name=label]').val('');
                $('[name=defaultValue]').val('');
                $('[name=type]').val('');
                $('[name=logicComment]').val('');
                $('[name=categoryLabel]').val('');
                $('[name=dsStatus]').val(0);
                $(ReactDOM.findDOMNode(this)).data('bootstrapValidator').enableFieldValidators('name', true);
            }
            this.setState({create,title,rowIndex,masterRowData});
        });
        event.eventEmitter.on(event.HIDE_CREATE_PARAMS_DIALOG,()=>{
            $(ReactDOM.findDOMNode(this)).modal('hide');
        });
    }
    componentWillUnmount(){
        event.eventEmitter.removeAllListeners(event.OPEN_CREATE_PARAMS_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_CREATE_PARAMS_DIALOG);
    }
    render(){
        const {dispatch, data, file}=this.props;
        const body=(
            <div style={{maxHeight: '60vh', overflow: 'auto', padding: '0 15px'}}>
                <div className="row">
                    <div className="form-group col-xs-6">
                        <label>字段名称：</label>
                        <input type="text" name="name" className="form-control"/>
                    </div>
                    <div className="form-group col-xs-6">
                        <label>标题：</label>
                        <input type="text" name="label" className="form-control"/>
                    </div>
                </div>
                <div className="row">
                    <div className="form-group col-xs-6">
                        <label>默认值:</label>
                        <input type="text" name="defaultValue" className="form-control"/>
                    </div>
                    <div className="form-group col-xs-6">
                        <label>数据类型:</label>
                        <select name="type" className="form-control">
                            {['String', 'Integer', 'Char', 'Double', 'Long', 'Float', 'BigDecimal', 'Boolean', 'Date', 'List', 'Set', 'Map', 'Enum', 'Object']
                                .map(option => 
                                    <option value={option} key={option}>{option}</option>
                                )}
                        </select>
                    </div>
                </div>
                <div className="form-group">
                    <label>实现逻辑:</label>
                    <textarea name="logicComment" className="form-control" rows="3" maxLength="300" />
                </div>
                <div className="row">
                    <div className="form-group col-xs-6">
                        <label>状态:</label>
                        <select name="dsStatus" className="form-control">
                            <option value="0">待开发</option>
                            <option value="1">已上线</option>
                        </select>
                    </div>
                </div>
                
            </div>
        );
        const buttons=[
            {
                name:'保存',
                className:'btn btn-success',
                icon:'fa fa-floppy-o',
                click:function () {
                    var validator=$(ReactDOM.findDOMNode(this)).data('bootstrapValidator');
                    validator.validate();
                    var valid=validator.isValid();
                    if(!valid){
                        return;
                    }
                    var {rowIndex,create,masterRowData}=this.state;
                    if(create){
                        dispatch(action.addVariable({
                            clazz: masterRowData.clazz,
                            name: $('[name=name]').val() || '',
                            label: $('[name=label]').val() || '',
                            dataType: $('[name=type]').val() || '',
                            defaultVal: $('[name=defaultValue]').val() || '',
                            logicComment: $('[name=logicComment]').val() || '',
                            categoryLabel: $('[name=categoryLabel]').val() || '',
                            dsStatus: Number($('[name=dsStatus]').val())
                        }, file));
                    }
                    event.eventEmitter.emit(event.SHOW_LOADING);
                    event.eventEmitter.emit(event.HIDE_CREATE_PARAMS_DIALOG);
                }.bind(this)
            }
        ];
        return (<CommonDialog title={this.state.title} body={body} buttons={buttons}/>);
    };
}
