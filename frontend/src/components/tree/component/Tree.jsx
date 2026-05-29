import '../css/tree.css';
import '../../../css/iconfont.css';
import TreeItem from './TreeItem.jsx';
import React, {Component} from 'react';
import {connect} from 'react-redux';

class Tree extends Component {
    render() {
        const {data, dispatch, draggable, treeType} = this.props;
        if (data) {
            // Render children directly, skip the root node itself
            const items = data.children || [];
            return (
                <div className="tree">
                    <ul>
                        {items.map((child, index) => (
                            <TreeItem key={child.id || (child.fullPath + '_' + index)} data={child} dispatch={dispatch} treeType={treeType}
                                      expandLevel={this.props.expandLevel} draggable={draggable}/>
                        ))}
                    </ul>
                </div>
            );
        } else {
            return (<div className="tree"><ul/></div>);
        }
    }
}

Tree.defaultProps = {expandLevel: 3};

function selector(state, ownProps) {
    console.log(state)
    return {
        data: ownProps.treeType === 'public' ? state.publicResource : state.data
    };
}

export default connect(selector)(Tree);