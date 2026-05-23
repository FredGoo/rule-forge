import React,{Component,PropTypes} from 'react';
import ReactDOM from 'react-dom';
import CommonDialog from '../components/dialog/component/CommonDialog.jsx';
import * as event from './event.js';
import * as frameEvent from '../frame/event.js';

export default class ReferenceDialog extends Component{
    constructor(props){
        super(props);
        this.state={
            title:'',
            files:[],
            fromResourceEditor: false,
            projectFilter: '',
            projectNames: [], // 存储项目名称列表
            currentData: null, // 存储当前的查询数据
            currentInfo: null,  // 存储当前的查询信息
            searchText: '', // 搜索框文本
            showDropdown: false // 是否显示下拉列表
        };

        // 绑定方法到this
        this.handleProjectFilterChange = this.handleProjectFilterChange.bind(this);
        this.handleSearchChange = this.handleSearchChange.bind(this);
        this.handleProjectSelect = this.handleProjectSelect.bind(this);
        this.toggleDropdown = this.toggleDropdown.bind(this);
        this.handleClickOutside = this.handleClickOutside.bind(this);
    }

    componentDidMount(){
        console.log('ReferenceDialog componentDidMount');

        // 监听项目列表变化事件 - 使用frame的eventEmitter
        frameEvent.eventEmitter.on(frameEvent.PROJECT_LIST_CHANGE, (projectNames) => {
            console.log('ReferenceDialog received projectNames from event:', projectNames);
            this.setState({ projectNames });
        });

        // 主动获取项目列表（如果frame已经加载了数据）
        this.loadProjectNames();

        // 从服务器获取项目列表
        this.loadProjectNamesFromServer();

        // 添加点击外部区域关闭下拉框的监听
        document.addEventListener('click', this.handleClickOutside);

        event.eventEmitter.on(event.OPEN_REFERENCE_DIALOG,(data,info,options={})=>{
            $(ReactDOM.findDOMNode(this)).modal('show');
            // 智能解码路径：如果包含%则解码，否则直接使用
            const path = data.path.includes('%') ? decodeURIComponent(data.path) : data.path;
            // 判断是否来自规则集（规则集的info通常包含"规则集"字样）
            const isFromRuleset = info && info.includes('规则集');
            const title = isFromRuleset ? `引用文件[${path}]的文件` : `引用文件[${path}]${info}的文件`;

            this.setState({
                fromResourceEditor: options.fromResourceEditor || false,
                projectFilter: '',
                searchText: '', // 重置搜索文本
                showDropdown: false, // 重置下拉框状态
                currentData: data, // 保存完整的data对象
                currentInfo: info // 保存info参数
            });
            this.loadReferenceFiles(data, '', info); // 传入完整的data对象和info
        });
        event.eventEmitter.on(event.CLOSE_REFERENCE_DIALOG,()=>{
            $(ReactDOM.findDOMNode(this)).modal('hide');
            // 重置状态
            this.setState({
                projectFilter: '',
                searchText: '',
                showDropdown: false
            });
        });
    }

    loadProjectNames() {
        console.log('ReferenceDialog loadProjectNames called');
        // 尝试从DOM中获取项目列表
        const projectMenu = $('#__project_filter_menu');
        console.log('projectMenu found:', projectMenu.length);
        if (projectMenu.length > 0) {
            const projectNames = [];
            projectMenu.find('li').each(function(index, li) {
                const $li = $(li);
                if (!$li.hasClass('_firstItem')) {
                    const link = $li.find('a');
                    const projectName = link.text().trim();
                    console.log('Found project name:', projectName);
                    if (projectName) {
                        projectNames.push(projectName);
                    }
                }
            });
            if (projectNames.length > 0) {
                console.log('ReferenceDialog loaded projectNames from DOM:', projectNames);
                this.setState({ projectNames });
            } else {
                console.log('No project names found in DOM');
            }
        } else {
            console.log('Project menu not found in DOM');
        }
    }

    loadProjectNamesFromServer() {
        console.log('ReferenceDialog loadProjectNamesFromServer called');
        // 直接从服务器获取项目列表
        fetch(window._server + '/frame/loadProjects', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({classify: true, projectDetail: false}).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then((data) => {
            if (data && data.repo && data.repo.projectNames) {
                console.log('ReferenceDialog loaded projectNames from server:', data.repo.projectNames);
                this.setState({ projectNames: data.repo.projectNames });
            }
        }).catch((error) => {
            console.log('Failed to load project names from server:', error);
        });
    }

    loadReferenceFiles(data, project = '', info = null) {
        const requestData = Object.assign({}, data);
        if (project) {
            requestData.project = project;
        }

        fetch(window._server + '/common/loadReferenceFiles', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams(requestData).toString()
        }).then(function(response) {
            if (!response.ok) throw response;
            return response.json();
        }).then((files) => {
            // 智能解码路径：如果包含%则解码，否则直接使用
            const path = data.path.includes('%') ? decodeURIComponent(data.path) : data.path;
            // 判断是否来自规则集，决定是否包含info
            const currentInfo = info || this.state.currentInfo;
            const isFromRuleset = currentInfo && currentInfo.includes('规则集');
            const title = isFromRuleset ? `引用文件[${path}]的文件` : `引用文件[${path}]${currentInfo || ''}的文件`;

            // 服务器返回的文件列表已经是解码后的路径，直接使用
            this.setState({ files: files, title });
        }).catch(() => {
            alert('加载引用文件信息失败.');
        });
    }

    componentWillUnmount(){
        // 清理事件监听器
        frameEvent.eventEmitter.removeAllListeners(frameEvent.PROJECT_LIST_CHANGE);
        event.eventEmitter.removeAllListeners(event.OPEN_REFERENCE_DIALOG);
        event.eventEmitter.removeAllListeners(event.CLOSE_REFERENCE_DIALOG);
        document.removeEventListener('click', this.handleClickOutside);
    }

    handleClickOutside(event) {
        // 检查点击是否在搜索框或下拉框外部
        const searchContainer = event.target.closest('.project-search-container');
        if (!searchContainer) {
            this.setState({ showDropdown: false });
        }
    }

    handleProjectFilterChange(e) {
        const selectedProject = e.target.value;
        this.setState({ projectFilter: selectedProject });

        // 重新调用接口获取筛选后的文件
        if (this.state.currentData) {
            this.loadReferenceFiles(this.state.currentData, selectedProject, this.state.currentInfo);
        }
    }

    handleSearchChange(e) {
        const searchText = e.target.value;
        this.setState({ searchText });
    }

    handleProjectSelect(projectName) {
        this.setState({
            projectFilter: projectName,
            searchText: projectName,
            showDropdown: false
        });

        // 重新调用接口获取筛选后的文件
        if (this.state.currentData) {
            this.loadReferenceFiles(this.state.currentData, projectName, this.state.currentInfo);
        }
    }

    toggleDropdown() {
        this.setState(prevState => ({ showDropdown: !prevState.showDropdown }));
    }

    getFilteredProjectNames() {
        const { projectNames, searchText } = this.state;
        if (!searchText) return projectNames;
        return projectNames.filter(name =>
            name.toLowerCase().includes(searchText.toLowerCase())
        );
    }

    render(){
        const { fromResourceEditor, projectFilter, projectNames, files, searchText, showDropdown } = this.state;
        const filteredProjectNames = this.getFilteredProjectNames();

        console.log('ReferenceDialog render - projectNames:', projectNames);
        console.log('ReferenceDialog render - fromResourceEditor:', fromResourceEditor);

        const body=(
            <div>
                {fromResourceEditor && (
                    <div style={{ marginBottom: '10px' }}>
                        <label style={{ marginRight: '8px', fontWeight: 'bold' }}>项目名称:</label>
                        <div style={{ position: 'relative', display: 'inline-block' }} className="project-search-container">
                            <input
                                type="text"
                                value={searchText}
                                onChange={this.handleSearchChange}
                                onFocus={() => this.setState({ showDropdown: true })}
                                placeholder="搜索项目名称..."
                                style={{
                                    minWidth: '150px',
                                    padding: '4px',
                                    border: '1px solid #ccc',
                                    borderRadius: '3px'
                                }}
                            />
                            {showDropdown && (
                                <div style={{
                                    position: 'absolute',
                                    top: '100%',
                                    left: 0,
                                    right: 0,
                                    backgroundColor: 'white',
                                    border: '1px solid #ccc',
                                    borderRadius: '3px',
                                    maxHeight: '200px',
                                    overflowY: 'auto',
                                    zIndex: 1000,
                                    boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
                                }}>
                                    <div
                                        style={{
                                            padding: '4px 8px',
                                            cursor: 'pointer',
                                            borderBottom: '1px solid #eee'
                                        }}
                                        onClick={() => this.handleProjectSelect('')}
                                    >
                                        全部项目
                                    </div>
                                    {filteredProjectNames.map(name => (
                                        <div
                                            key={name}
                                            style={{
                                                padding: '4px 8px',
                                                cursor: 'pointer',
                                                borderBottom: '1px solid #eee',
                                                backgroundColor: projectFilter === name ? '#f0f0f0' : 'white'
                                            }}
                                            onClick={() => this.handleProjectSelect(name)}
                                        >
                                            {name}
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                )}
                <table className="table table-bordered">
                    <thead>
                        <tr><td>文件路径</td><td style={{width:'100px'}}>类型</td><td style={{width:'80px'}}>操作</td></tr>
                    </thead>
                    <tbody>
                    {
                        files.map(function (file, index) {
                            return (
                                <tr key={index}>
                                    <td>{file.path}</td>
                                    <td>{file.type}</td>
                                    <td><button type="button" className="btn btn-link" style={{padding:'5px 5px'}} onClick={function(e) {
                                        const editorPath = '/html'+(file.editor);
                                        const url = '.' + editorPath + '?file=' + file.path;
                                        console.log('url:', url);
                                        const config={
                                            id:file.path,
                                            name:file.name,
                                            fullPath:file.path, // 保持解码后的路径用于显示
                                            path:url, // 完整的URL用于iframe
                                            active: true
                                        };
                                        // 触发TREE_NODE_CLICK事件，让文件在右侧iframe中打开
                                        window.parent.componentEvent.eventEmitter.emit(window.parent.componentEvent.TREE_NODE_CLICK,config);
                                    }}>设计器中打开</button></td>
                                </tr>
                            );
                        })
                    }
                    </tbody>
                </table>
            </div>
        );
        return (<CommonDialog buttons={[]} body={body} title={this.state.title}/>);
    }
}
