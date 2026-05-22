import '../css/tree.css';
import '../../../css/iconfont.css';
import TreeItem from './TreeItem.jsx';
import React, {Component} from 'react';
import {connect} from 'react-redux';

class Tree extends Component {
    render() {
        const {data, dispatch, draggable, treeType} = this.props;
        console.log(treeType)
        if (data) {
            return (
                <ul style={{paddingLeft: '20px'}}>
                    <TreeItem data={data} dispatch={dispatch} treeType={treeType} expandLevel={this.props.expandLevel}
                              draggable={draggable}/>
                </ul>
            );
        } else {
            return (<ul/>);
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