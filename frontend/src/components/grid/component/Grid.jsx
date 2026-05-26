import '../css/grid.css';
import React, {Component} from 'react';
import Row from './Row.jsx';
import CellEditor from './CellEditor.jsx';
import {uniqueID} from '../../componentAction.js';

class Grid extends Component {
    constructor(props) {
        super(props);
        this.state = {display: 'none', filterTexts: {}};
    }

    onFilter(colIndex, e) {
        if (e.key !== 'Enter') {
            return;
        }
        const value = e.target.value;
        const name = e.target.name;
        const oldData = this.state.filterTexts[name];
        if (value === oldData) {
            return;
        }
        this.setState(prevState => ({
            filterTexts: {...prevState.filterTexts, [name]: value}
        }));
    }

    _matchesFilter(rowData, headers) {
        const filterTexts = this.state.filterTexts;
        const filterNames = Object.keys(filterTexts);
        for (let i = 0; i < filterNames.length; i++) {
            const filterName = filterNames[i];
            const filterValue = filterTexts[filterName];
            if (!filterValue) continue;
            const header = headers.find(h => h.id === filterName);
            if (!header) continue;
            let cellValue = rowData[header.name];
            if (cellValue && typeof cellValue === 'object') {
                cellValue = JSON.stringify(cellValue);
            }
            const cellStr = cellValue != null ? String(cellValue) : '';
            if (cellStr.indexOf(filterValue) === -1) {
                return false;
            }
        }
        return true;
    }

    render() {
        const {headers, operationConfig, dispatch, selectFirst, uniqueKey} = this.props;
        const rows = this.props.rows || [];
        const headerContent = [];
        const bodyContent = [];
        headers.forEach((header, index) => {
            if (header.editable) {
                headerContent.push(
                    <td key={uniqueID()} style={{width: header.width}}>
                        <label>{header.label}</label>
                        <CellEditor onchange={this.props.onchange} onblur={(e) => {
                            this.setState({display: 'none'});
                            this.props.onblur(e);
                        }} header={header} display={this.state.display}/>
                    </td>
                );
            } else {
                headerContent.push(
                    <td key={uniqueID()} style={{width: header.width}}>
                        <label>{header.label}</label>
                    </td>
                );
            }
        });
        if (operationConfig) {
            headerContent.push(
                <td key={uniqueID()} style={{width: operationConfig.width}}><label>操作列</label></td>
            )
        }
        const filterRow = (
            <tr key='filterrow' style={{background: 'var(--rf-bg-base)'}}>
                {headers.map((header, index) => {
                    if (header.filterable) {
                        return (<td key={uniqueID()}>
                            <input type="text" onKeyPress={this.onFilter.bind(this, index)} name={header.id}
                                   className="form-control" style={{height: '28px'}}
                                   placeholder='请输入过滤条件，回车查询...'/>
                        </td>);
                    } else if (!header.hideFilterRow) {
                        return (<td key={uniqueID()}>&nbsp;</td>);
                    }
                })}
                {operationConfig ? (<td></td>) : null}
            </tr>
        );
        bodyContent.push(filterRow);
        rows.forEach((row, rowIndex) => {
            if (!row.id) {
                row.id = uniqueID();
            }
            if (!this._matchesFilter(row, headers)) {
                return;
            }
            var rowKey = row.id;
            if (rowIndex === 0 && selectFirst) {
                bodyContent.push(
                    <Row key={rowKey} select={true} ready={this.props.ready} headers={headers} dispatch={dispatch}
                         rowClick={this.props.rowClick} operations={operationConfig ? operationConfig.operations : null}
                         rowData={row} rowIndex={rowIndex}/>
                );
            } else {
                bodyContent.push(
                    <Row key={rowKey} ready={this.props.ready} headers={headers} dispatch={dispatch}
                         rowClick={this.props.rowClick} operations={operationConfig ? operationConfig.operations : null}
                         rowData={row} rowIndex={rowIndex}/>
                );
            }
        });
        const tableStyle = {margin: 0, width: (this.props.width ? this.props.width : '100%')};
        return (
            <table className="table table-bordered" style={tableStyle}>
                <thead>
                <tr className="well">{headerContent}</tr>
                </thead>
                <tbody>{bodyContent}</tbody>
            </table>
        );
    }
}

export default Grid;
