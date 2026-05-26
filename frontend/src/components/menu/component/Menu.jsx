import React, {Component} from 'react';
import MenuItem from './MenuItem.jsx';

export default class Menu extends Component {
    render() {
        const {items, data, dispatch, menuId, visible, x, y} = this.props;
        let result = [];
        items.forEach((item, index) => {
            result.push(
                <MenuItem item={item} key={index} data={data} dispatch={dispatch}/>
            );
        });
        const menuStyle = {
            color: 'var(--rf-text-primary)',
            display: visible ? 'block' : 'none',
            position: 'fixed',
            left: x || 0,
            top: y || 0
        };
        return (
            <div id={menuId}>
                <ul className="dropdown-menu" style={menuStyle}>{result}</ul>
            </div>
        );
    }
}