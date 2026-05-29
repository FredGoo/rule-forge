import React,{Component,PropTypes} from 'react';
import * as event from '../event.js';
import CommonDialog from '../../components/dialog/component/CommonDialog.jsx';
import IFrame from '../../components/frametab/component/IFrame.jsx';

export default class ReteDiagramDialog extends Component{
    constructor(props){
        super(props);
        this.state={visible: false, path:null};
    }
    componentDidMount(){
        event.eventEmitter.on(event.OPEN_RETE_DIAGRAM_DIALOG,(files)=>{
            this.setState({visible: true});
            const path=window._server+"/retediagram?_r="+Math.random()+"&files="+files;
            this.setState({path});
        });
        event.eventEmitter.on(event.HIDE_RETE_DIAGRAM_DIALOG,()=>{
            this.setState({visible: false});
        });
    }
    render(){
        let body=(<div></div>);
        if(this.state.path){
            body=(
                <div style={{minHeight:'500px'}}>
                    <IFrame path={this.state.path}/>
                </div>
            );
        }
        const buttons=[
            {
                name:'关闭窗口',
                className:'btn btn-primary',
                icon:'fa fa-close',
                click:function () {
                    event.eventEmitter.emit(event.HIDE_RETE_DIAGRAM_DIALOG);
                }
            }
        ];
        return (
            <CommonDialog visible={this.state.visible} large={true} title="RETE树展示" body={body} buttons={buttons} onClose={() => this.setState({visible: false})}/>
        );
    }
};