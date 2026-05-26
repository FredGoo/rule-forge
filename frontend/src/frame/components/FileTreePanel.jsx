import React, {Component} from 'react';
import * as ACTIONS from '@/frame/action.js';
import Tree from '@/components/tree/component/Tree.jsx';

export default class FileTreePanel extends Component {
    render() {
        return (
            <div className="file-tree-panel">
                <div className="file-tree-search">
                    <div className="file-tree-search-wrapper">
                        <i className="glyphicon glyphicon-search file-tree-search-icon"/>
                        <input type="text" className="form-control fileSearchText file-tree-search-input"
                               placeholder="搜索文件..."
                               onKeyDown={(e) => {
                                   if (e.key === 'Enter') {
                                       window.searchFileName = e.target.value;
                                       this.props.store.dispatch(
                                           ACTIONS.loadData(true, window._projectName, null, e.target.value)
                                       );
                                   }
                               }}/>
                    </div>
                </div>
                <div className="file-tree-content">
                    <Tree draggable={true}/>
                </div>
            </div>
        );
    }
}
