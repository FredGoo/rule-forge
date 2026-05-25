import '../css/grid.css';
import React, {Component} from 'react';
import Cell from './Cell.jsx';
import {uniqueID} from '../../componentAction.js';

export default class Row extends Component {
    constructor(props) {
        super(props);
        this.trRef = React.createRef();
    }

    _handleClick = (e) => {
        const {rowData, rowIndex, rowClick} = this.props;
        if (rowClick) {
            rowClick(rowData, rowIndex);
        }
        const tr = this.trRef.current;
        if (tr) {
            const siblings = tr.parentElement.children;
            for (let i = 0; i < siblings.length; i++) {
                siblings[i].classList.remove('bg-warning');
            }
            tr.classList.add('bg-warning');
        }
    };

    render() {
        const {headers, rowData, rowIndex, operations, select} = this.props;
        const tds = [];
        headers.forEach((header, headerIndex) => {
            tds.push(
                <Cell key={uniqueID()} onchange={(newValue) => {
                    rowData[header.name] = newValue;
                }} rowData={rowData} header={header}/>
            );
        });
        if (operations) {
            const key = 'op-td' + rowIndex;
            tds.push(
                <td key={uniqueID()} style={{padding: "5px 5px"}}>
                    {
                        operations.map((op, index) => {
                            if (op.icon) {
                                return (
                                    <i key={uniqueID()} className={op.icon} title={op.label} style={op.style}
                                       onClick={op.click.bind(this, rowIndex, rowData)}/>
                                );
                            } else {
                                return (
                                    <button key={uniqueID()} type="button" className="btn btn-link"
                                            style={{padding: '0px 1px'}}
                                            onClick={op.click.bind(this, rowIndex, rowData)}>{op.label}
                                    </button>
                                );
                            }
                        })
                    }
                </td>
            );
        }
        let trClass = select ? 'bg-warning' : '';
        trClass += ' content-tr';
        return (
            <tr ref={this.trRef} style={{height: '26px'}} className={trClass} onClick={this._handleClick}>
                {tds}
            </tr>
        );
    }
}
