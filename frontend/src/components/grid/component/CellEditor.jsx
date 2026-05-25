import React, {Component} from 'react';
import * as componentEvent from '../componentEvent.js';

export default class CellEditor extends Component {
    constructor(props) {
        super(props);
        this.state = {display: 'none'};
        this.inputRef = React.createRef();
    }

    componentDidMount() {
        componentEvent.eventEmitter.on(componentEvent.SHOW_CELL_EDITOR, (data) => {
            const header = this.props.header;
            if (data.colId !== header.id) {
                return;
            }
            const rowData = data.rowData;
            this.setState({rowData, display: '', value: rowData[header.name]});
        });
    }

    componentWillUnmount() {
        componentEvent.eventEmitter.removeAllListeners(componentEvent.SHOW_CELL_EDITOR);
    }

    blur() {
        const value = this.inputRef.current ? this.inputRef.current.value : '';
        const header = this.props.header;
        const rowData = this.state.rowData;
        if (rowData) {
            rowData[header.name] = value;
        }
        this.setState({display: 'none'});
    }

    componentDidUpdate(prevProps, prevState) {
        if (this.state.display === '' && prevState.display !== '' && this.inputRef.current) {
            this.inputRef.current.focus();
        }
    }

    render() {
        const styleObj = {display: this.state.display, height: '31px', padding: '0px 5px'};
        const header = this.props.header;
        const {editorType, selectData, selectParam} = header;
        const currentValue = this.state.value || '';
        switch (editorType) {
            case "select":
                let selectOptions = [];
                selectOptions = selectParam && this.state.rowData && this.state.rowData[selectParam] ? this.state.rowData[selectParam] : selectData;
                return (<select ref={this.inputRef} style={styleObj} onBlur={this.blur.bind(this)}
                                className="form-control" defaultValue={currentValue}>
                    {selectOptions.map((option, index) => {
                        return (<option key={index}>{option}</option>);
                    })}
                </select>);
            case "boolean":
                return (
                    <select ref={this.inputRef} onBlur={this.blur.bind(this)} className="form-control"
                            defaultValue={currentValue}>
                        <option value="true">true</option>
                        <option value="false">false</option>
                    </select>
                );
            case "date":
                return (<input ref={this.inputRef} style={styleObj} onBlur={this.blur.bind(this)} type="date"
                               className="form-control" defaultValue={currentValue}/>);
            case "number":
                return (<input ref={this.inputRef} style={styleObj} onBlur={this.blur.bind(this)} type="number"
                               className="form-control" defaultValue={currentValue}/>);
            default:
                return (<input ref={this.inputRef} style={styleObj} onBlur={this.blur.bind(this)} type="text"
                               className="form-control" defaultValue={currentValue}/>);
        }
    }
};
