import React, {Component} from 'react';
import * as ACTIONS from '@/frame/action.js';
import * as event from '@/frame/event.js';
import * as componentEvent from '@/components/componentEvent.js';

export default class TopBar extends Component {
    constructor(props) {
        super(props);
        this.state = {
            projects: [],
            selectedProject: null,
            tabs: [],
            activeTab: null,
            projectDropdownOpen: false,
            userDropdownOpen: false
        };
        this._handleClickOutside = this._handleClickOutside.bind(this);
    }

    componentDidMount() {
        const {eventObj} = this.props;
        eventObj.eventEmitter.on(eventObj.PROJECT_LIST_CHANGE, projectNames => {
            this.setState({projects: projectNames});
            if (projectNames.length > 0 && !this.state.selectedProject) {
                this._selectProject(projectNames[0]);
            }
        });
        document.addEventListener('click', this._handleClickOutside);
    }

    componentWillUnmount() {
        document.removeEventListener('click', this._handleClickOutside);
    }

    _handleClickOutside(e) {
        if (this.state.projectDropdownOpen || this.state.userDropdownOpen) {
            if (!e.target.closest('.topbar-project-selector') && !e.target.closest('.topbar-user')) {
                this.setState({projectDropdownOpen: false, userDropdownOpen: false});
            }
        }
    }

    _selectProject(name) {
        window._projectName = name;
        this.setState({selectedProject: name});
        this.props.store.dispatch(ACTIONS.loadData(true, name));
        this.props.eventObj.eventEmitter.emit(this.props.eventObj.PROJECT_FILTER_CHANGE, name);
    }

    _handleCreateProject() {
        this.props.eventObj.eventEmitter.emit(event.OPEN_NEW_PROJECT_DIALOG, {type: 'root'});
        this.setState({projectDropdownOpen: false});
    }

    _handleLogout(e) {
        e.preventDefault();
        fetch(window._server + '/frame/logout', {method: 'POST'}).then(function () {
            window.location.href = 'html/login.html';
        });
    }

    setTabData(tabs, activeTab) {
        this.setState({tabs, activeTab});
    }

    render() {
        const {projects, selectedProject, tabs, activeTab, projectDropdownOpen, userDropdownOpen} = this.state;
        const {frameTabRef} = this.props;
        const username = (window.__currentUser && window.__currentUser.username) || 'admin';

        return (
            <div className="topbar">
                <div className="topbar-left">
                    <div className="topbar-project-selector">
                        <button className="topbar-project-btn" onClick={(e) => {
                            e.stopPropagation();
                            this.setState({projectDropdownOpen: !projectDropdownOpen, userDropdownOpen: false});
                        }}>
                            <i className="rf rf-project" style={{marginRight: 6, fontSize: 14}}/>
                            <span>{selectedProject || '选择项目'}</span>
                            <i className="glyphicon glyphicon-chevron-down" style={{marginLeft: 6, fontSize: 10, opacity: 0.5}}/>
                        </button>
                        {projectDropdownOpen && (
                            <div className="topbar-dropdown topbar-project-dropdown">
                                {projects.map(name => (
                                    <div key={name}
                                         className={'topbar-dropdown-item' + (name === selectedProject ? ' active' : '')}
                                         onClick={() => {
                                             this._selectProject(name);
                                             this.setState({projectDropdownOpen: false});
                                         }}>
                                        <i className={name === selectedProject ? 'rf rf-check' : ''} style={{width: 16}}/>
                                        {name}
                                    </div>
                                ))}
                                <div className="topbar-dropdown-divider"/>
                                <div className="topbar-dropdown-item" onClick={() => this._handleCreateProject()}>
                                    <i className="rf rf-createpro" style={{width: 16, fontSize: 12}}/>
                                    创建新项目
                                </div>
                            </div>
                        )}
                    </div>
                </div>

                <div className="topbar-tabs">
                    {tabs.map(tab => (
                        <div key={tab.fullPath}
                             className={'topbar-tab' + (tab.fullPath === activeTab ? ' active' : '')}>
                            <span className="topbar-tab-label" onClick={() => {
                                if (frameTabRef) frameTabRef.activateTab(tab.fullPath);
                            }}>{tab.label}</span>
                            <button className="topbar-tab-close" onClick={(e) => {
                                e.stopPropagation();
                                if (frameTabRef) frameTabRef.closeTab(tab.fullPath);
                            }}>×</button>
                        </div>
                    ))}
                </div>

                <div className="topbar-right">
                    <div className="topbar-user">
                        <button className="topbar-user-btn" onClick={(e) => {
                            e.stopPropagation();
                            this.setState({userDropdownOpen: !userDropdownOpen, projectDropdownOpen: false});
                        }}>
                            <div className="topbar-user-avatar">{username.charAt(0).toUpperCase()}</div>
                        </button>
                        {userDropdownOpen && (
                            <div className="topbar-dropdown topbar-user-dropdown">
                                <div className="topbar-dropdown-info">
                                    <div className="topbar-user-avatar" style={{width: 32, height: 32, fontSize: 14}}>
                                        {username.charAt(0).toUpperCase()}
                                    </div>
                                    <span>{username}</span>
                                </div>
                                <div className="topbar-dropdown-divider"/>
                                <div className="topbar-dropdown-item" onClick={(e) => {
                                    this.setState({userDropdownOpen: false});
                                    componentEvent.eventEmitter.emit(componentEvent.TREE_NODE_CLICK, {
                                        id: 'security_config_',
                                        name: '资源权限配置',
                                        fullPath: 'security_config_',
                                        path: './html/permission-config-editor.html'
                                    });
                                }}>
                                    <i className="rf rf-authority" style={{width: 16, fontSize: 12}}/>
                                    权限配置
                                </div>
                                <div className="topbar-dropdown-item" onClick={this._handleLogout.bind(this)}>
                                    <i className="glyphicon glyphicon-log-out" style={{width: 16, fontSize: 12}}/>
                                    退出登录
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        );
    }
}
