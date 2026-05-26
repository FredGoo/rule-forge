import React, {Component} from 'react';
import * as event from '../../../frame/event.js';

export default class Dialog extends Component {
    constructor(props) {
        super(props);
        this.state = {
            title: this.props.title || '',
            buttons: this.props.buttons || [],
            body: this.props.body || [],
            visible: this.props.visible || false,
            init: null,
            destroy: null
        };
    }

    componentDidMount() {
        event.eventEmitter.on(event.OPEN_DIALOG, (data) => {
            this.setState({
                title: data.title || this.state.title,
                body: data.body || this.state.body,
                buttons: data.buttons || this.state.buttons,
                init: data.init,
                destroy: data.destroy,
                visible: true
            });
        });
        event.eventEmitter.on(event.CLOSE_DIALOG, () => {
            this.setState({visible: false});
        });
        event.eventEmitter.on(event.DIALOG_CONTNET_CHANGE, (data) => {
            this.setState({
                title: data.title || this.state.title,
                body: data.body || this.state.body,
                buttons: data.buttons || this.state.buttons
            });
        });
    }

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.OPEN_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_DIALOG);
    }

    componentDidUpdate(prevProps, prevState) {
        if (this.props.visible !== undefined && this.props.visible !== prevProps.visible) {
            this.setState({visible: this.props.visible});
        }
        if (!prevState.visible && this.state.visible && this.state.init) {
            this.state.init(this.props.dispatch);
        }
    }

    render() {
        const {visible, title, body, buttons} = this.state;
        const buttonElements = (buttons || []).map((btn, index) => (
            <button type="button" key={index} className={btn.className} onClick={() => btn.click(this.props.dispatch)}>
                <i className={btn.icon}/> {btn.name}
            </button>
        ));
        return (
            <div>
                {visible && <div className="modal-backdrop fade in"></div>}
                <div className={`modal fade ${visible ? 'in' : ''}`}
                     style={{display: visible ? 'block' : 'none'}}
                     tabIndex="-1" role="dialog" aria-hidden={!visible}>
                    <div className="modal-dialog">
                        <div className="modal-content">
                            <div className="modal-header" style={{borderBottom: '1px solid var(--rf-border-split)'}}>
                                <button type="button" className="close" data-dismiss="modal" aria-hidden="true">&times;</button>
                                <h4 className="modal-title" style={{fontWeight: 'var(--rf-font-weight-semibold)', color: 'var(--rf-text-primary)'}}>{title}</h4>
                            </div>
                            <div className="modal-body" style={{padding: 'var(--rf-space-6)', color: 'var(--rf-text-primary)'}}>{body}</div>
                            <div className="modal-footer">{buttonElements}</div>
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}
