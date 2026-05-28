import React,{Component} from 'react';
import * as event from '../../componentEvent.js';

export default class IFrame extends Component{
    constructor(props) {
        super(props);
        this.iframeRef = React.createRef();
        this._onResize = this._onResize.bind(this);
    }

    componentDidMount(){
        const iframe = this.iframeRef.current;

        iframe.addEventListener('load', function(){
            try {
                iframe.style.opacity = '1';
            } catch(e) {}
        });

        const docHeight = document.documentElement.scrollHeight;
        iframe.style.height = (docHeight - 47) + "px";

        window.addEventListener('resize', this._onResize);
    }

    _onResize() {
        const iframe = this.iframeRef.current;
        if (iframe) {
            iframe.style.height = document.documentElement.scrollHeight + "px";
        }
    }

    componentWillUnmount() {
        window.removeEventListener('resize', this._onResize);
    }

    render(){
        const path=encodeURI(encodeURI(this.props.path));
        const iframeId=this.props.id;
        return (
            <iframe ref={this.iframeRef} src={path} id={iframeId}
                    style={{width:'100%', border:0, opacity: 0, transition: 'opacity 0.2s'}}
                    frameBorder="none"></iframe>
        );
    }
}
