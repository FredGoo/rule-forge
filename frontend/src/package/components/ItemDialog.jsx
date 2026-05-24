import React,{Component,PropTypes} from 'react';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';
import * as componentEvent from '../../components/componentEvent.js';

export default class ItemDialog extends Component{
    constructor(props){
        super(props);
        this.state={visible: false, title:'', itemName:'', itemPath:'', itemVersion:'', errors:{}};
    }
    componentDidMount(){
        event.eventEmitter.on(event.OPEN_CREATE_PACKAGE_ITEM_DIALOG,(data)=>{
            this.setState({visible: true, errors:{}});
            const create=data.create;
            const title=data.title;
            const rowIndex=data.rowIndex;
            if(create){
                this.setState({create,title,rowIndex,itemName:'',itemPath:'',itemVersion:''});
            }else{
                this.setState({create,title,rowIndex,itemName:data.rowData.name,itemPath:data.rowData.path,itemVersion:data.rowData.version});
            }
        });
        event.eventEmitter.on(event.HIDE_CREATE_PACKAGE_ITEM_DIALOG,()=>{
            this.setState({visible: false});
        });
    }
    componentWillUnmount(){
        event.eventEmitter.removeAllListeners(event.OPEN_CREATE_PACKAGE_ITEM_DIALOG);
        event.eventEmitter.removeAllListeners(event.HIDE_CREATE_PACKAGE_ITEM_DIALOG);
    }
    _validate(){
        const errors={};
        const {itemName,itemPath,itemVersion}=this.state;
        if(!itemName||!itemName.trim()){
            errors.itemName='文件名不能为空';
        }
        if(!itemPath||!itemPath.trim()){
            errors.itemPath='文件路径不能为空';
        }
        if(!itemVersion||!itemVersion.trim()){
            errors.itemVersion='文件版本不能为空';
        }
        return {valid:Object.keys(errors).length===0,errors};
    }
    render(){
        const {dispatch}=this.props;
        const body=(
            <div>
                <div className="form-group">
                    <label>名称:</label>
                    <input type="text" className="form-control" name="itemName"
                        value={this.state.itemName}
                        onChange={(e)=>this.setState({itemName:e.target.value,errors:{...this.state.errors,itemName:undefined}})}/>
                    {this.state.errors.itemName && <div className="text-danger" style={{fontSize:'12px'}}>{this.state.errors.itemName}</div>}
                </div>
                <div className="form-group">
                    <label>资源文件路径:</label>
                    <div className="input-group">
                        <input type="text" className="form-control" name="itemPath" disabled
                            value={this.state.itemPath}/>
                        <span className="input-group-btn">
                            <button type="button" className="btn btn-default" onClick={()=>{
                                componentEvent.eventEmitter.emit(componentEvent.OPEN_KNOWLEDGE_TREE_DIALOG,{project:this.props.project,callback:function(file,version){
                                     this.setState({itemPath:'jcr:'+file,itemVersion:version});
                                }.bind(this)});
                            }}>选择文件</button>
                        </span>
                    </div>
                    {this.state.errors.itemPath && <div className="text-danger" style={{fontSize:'12px'}}>{this.state.errors.itemPath}</div>}
                </div>
                <div className="form-group">
                    <label>版本号:</label>
                    <input type="text" className="form-control" name="itemVersion" disabled
                        value={this.state.itemVersion}/>
                    {this.state.errors.itemVersion && <div className="text-danger" style={{fontSize:'12px'}}>{this.state.errors.itemVersion}</div>}
                </div>
            </div>
        );
        const buttons=[
            {
                name:'保存',
                className:'btn btn-success',
                icon:'fa fa-floppy-o',
                click:function () {
                    var {valid,errors}=this._validate();
                    if(!valid){
                        this.setState({errors});
                        return;
                    }
                    var {itemName,itemPath,itemVersion,create,rowIndex}=this.state;
                    if(create){
                        dispatch(action.addSlave({name:itemName,path:itemPath,version:itemVersion}));
                    }else{
                        dispatch(action.updateSlave({rowIndex,name:itemName,path:itemPath,version:itemVersion}));
                    }
                    event.eventEmitter.emit(event.HIDE_CREATE_PACKAGE_ITEM_DIALOG);
                }.bind(this)
            }
        ];
        return (<CommonDialog visible={this.state.visible} title={this.state.title} body={body} buttons={buttons}/>);
    };
}
