import '../../../node_modules/codemirror/mode/xml/xml.js';
import React,{Component,PropTypes} from 'react';
import CodeMirror from 'codemirror';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import * as event from '../event.js';
import * as action from '../action.js';

export default class SourceDialog extends Component{
    constructor(props){
        super(props);
        this.editorId='__file_source_editor';
        this.state={title:'', visible: false};
    }
    componentDidMount(){
        const editorId=this.editorId;
        const codeMirror=CodeMirror.fromTextArea(document.getElementById(editorId),{
            mode:'xml',
            lineNumbers:true
        });
        event.eventEmitter.on(event.OPEN_SOURCE_DIALOG,(file,content)=>{
            this.setState({file,codeMirror,title:`[${file}]源码`, visible: true});
            setTimeout(function () {
                const winHeight=window.innerHeight;
                const height=winHeight > 800 ? winHeight-160 : winHeight ;
                codeMirror.setSize('100%',height+'px');
                codeMirror.setValue(content);
                codeMirror.refresh();
            },400);
        });
        event.eventEmitter.on(event.CLOSE_SOURCE_DIALOG,()=>{
            this.setState({visible: false});
        });
    }
    componentWillUnmount(){
        event.eventEmitter.removeAllListeners(event.OPEN_SOURCE_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_SOURCE_DIALOG);
    }
    render(){
        const body=(
            <textarea id={this.editorId} rows="10"></textarea>
        );
        const buttons=[{
            name:'保存',
            className:'btn btn-success',
            icon:'rf rf-save',
            click:function () {
                const newContent=this.state.codeMirror.getValue(),fullPath=this.state.file;
                action.saveFileSource(fullPath,newContent);
            }.bind(this)
        }];
        return (<CommonDialog visible={this.state.visible} title={this.state.title} body={body} buttons={buttons} large={true}/>);
    }
}