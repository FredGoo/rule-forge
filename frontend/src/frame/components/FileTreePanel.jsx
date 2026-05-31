import {Component} from 'react';
import * as ACTIONS from '@/frame/action.js';
import * as event from '@/frame/event.js';
import Tree from '@/components/tree/component/Tree.jsx';
import PackageNavigator from '@/package/components/PackageNavigator.jsx';

export default class FileTreePanel extends Component {
    constructor(props) {
        super(props);
        this.state = {
            viewMode: 'tree'  // 'tree' or 'package'
        };
    }

    toggleViewMode = () => {
        this.setState(prev => ({viewMode: prev.viewMode === 'tree' ? 'package' : 'tree'}));
    }

    handlePackageFileSelect = (fileInfo) => {
        // Open the file in the editor with gitTag info
        const {store} = this.props;
        // Dispatch file open with gitTag for version-aware reading
        if (store && store.dispatch) {
            window._currentGitTag = fileInfo.gitTag;
            // Trigger file open through the existing event system
            event.eventEmitter.emit(event.OPEN_FILE, fileInfo);
        }
    }

    handleVersionChange = (_version, gitTag) => {
        window._currentGitTag = gitTag;
    }

    render() {
        const {viewMode} = this.state;
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
                    {/* View mode toggle button */}
                    <button className="btn btn-default btn-xs"
                            style={{marginLeft: '4px', padding: '2px 8px'}}
                            onClick={this.toggleViewMode}
                            title={viewMode === 'tree' ? '切换到知识包视图' : '切换到文件树视图'}>
                        <i className={viewMode === 'tree' ? 'rf rf-package' : 'rf rf-tree'}/>
                    </button>
                </div>
                <div className="file-tree-content">
                    {viewMode === 'tree' ? (
                        <Tree draggable={true}/>
                    ) : (
                        <PackageNavigator
                            project={window._projectName}
                            onFileSelect={this.handlePackageFileSelect}
                            onVersionChange={this.handleVersionChange}
                        />
                    )}
                </div>
            </div>
        );
    }
}
