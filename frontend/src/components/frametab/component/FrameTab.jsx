import React, {Component} from 'react';
import QuickStart from '../../../frame/QuickStart.jsx';
import IFrame from './IFrame.jsx';
import * as event from '../../componentEvent.js';
import * as action from '../../../frame/action.js';
import Menu from '../../menu/component/Menu.jsx';
import {nextIFrameId} from '../../../Utils.js';

export default class FrameTab extends Component {
    constructor(props) {
        super(props);
        this.state = {data: [], activeContextMenuId: null, contextMenuX: 0, contextMenuY: 0};
        this._handleContextMenu = this._handleContextMenu.bind(this);
        this._handleClickOutside = this._handleClickOutside.bind(this);
    }

    addTab(newTabData) {
        let data = this.state.data, exist = false, fullPath = this._processFullPath(newTabData.fullPath);

        for (let item of data) {
            if (this._processFullPath(item.fullPath) === fullPath) {
                exist = true;
            }
        }
        if (exist) {
            setTimeout(function () {
                const el = document.getElementById('tabLink' + fullPath);
                if (el) el.click();
            }, 100);
            return;
        }
        if (!exist) {
            data.push(newTabData);
            this.setState({data});
        }
        setTimeout(function () {
            const el = document.getElementById('tabLink' + fullPath);
            if (el) el.click();
        }, 100);
    }

    componentDidMount() {
        event.eventEmitter.on(event.TREE_NODE_CLICK, (data) => {
            this.addTab(data);
        });
        document.addEventListener('click', this._handleClickOutside);
    };

    componentWillUnmount() {
        event.eventEmitter.removeAllListeners(event.TREE_NODE_CLICK);
        document.removeEventListener('click', this._handleClickOutside);
    }

    _handleContextMenu(e, menuId) {
        e.preventDefault();
        e.stopPropagation();
        this.setState({
            activeContextMenuId: menuId,
            contextMenuX: e.clientX,
            contextMenuY: e.clientY
        });
    }

    _handleClickOutside() {
        if (this.state.activeContextMenuId) {
            this.setState({activeContextMenuId: null});
        }
    }

    _processFullPath(fullPath) {
        fullPath = fullPath.replace(new RegExp('/', 'gm'), '');
        fullPath = fullPath.replace(new RegExp('\\.', 'gm'), '');
        fullPath = fullPath.replace(new RegExp(':', 'gm'), '');
        return fullPath;
    }

    render() {
        const data = this.state.data, {welcomePage} = this.props;
        let tabs = [], tabContainers = [];
        data.forEach((item, index) => {
            const fullPath = this._processFullPath(item.fullPath), tabContainerId = 'iframeTab-' + fullPath,
                tableContainerLink = '#' + tabContainerId;
            const active = '', paneClass = 'tab-pane ' + active, key = 'key' + fullPath;
            const liId = 'li' + fullPath, linkId = 'tabLink' + fullPath, menuId = 'tabmenu' + fullPath;
            const iframeId = nextIFrameId();
            tabContainers.push(
                <div className={paneClass} id={tabContainerId} key={key}>
                    <IFrame id={iframeId} path={item.path}/>
                </div>
            );
            const fileName = item.name;
            const pointPos = fileName.indexOf('.');
            const fileType = fileName.substring(pointPos + 1, fileName.length);
            let type = '';
            if (fileType === '推送客户端配置') {
                type = '>>' + item.project;
            } else if (fileType === '资源权限配置') {
                type = 'AUTH';
            } else if (fileType === '客户端访问权限配置') {
                type = 'AUTH';
            } else {
                type = action.buildType(fileType);
            }
            if (type === 'package') {
                type = item.fullPath.substring(1, item.fullPath.length);
            }
            tabs.push(
                <li id={liId} className={active} key={key} onContextMenu={(e) => this._handleContextMenu(e, menuId)}>
                    <a id={linkId} href={tableContainerLink} data-toggle="tab">
                        <button className="close closeTab frame-tab-close" type="button" onClick={() => {
                            const frame = document.getElementById(iframeId);
                            if (frame && frame.contentWindow && frame.contentWindow._dirty) {
                                const result = confirm('当前页面内容未保存，确实要关闭吗？');
                                if (!result) {
                                    return;
                                }
                            }
                            let pos = data.indexOf(item);
                            data.splice(pos, 1);
                            let nextLinkId;
                            if (pos > 0) {
                                nextLinkId = 'tabLink' + this._processFullPath(data[pos - 1].fullPath);
                            } else if (data.length > 0) {
                                data[data.length - 1].active = true;
                                nextLinkId = 'tabLink' + this._processFullPath(data[data.length - 1].fullPath);
                            }
                            this.setState({data});
                            if (nextLinkId) {
                                setTimeout(function () {
                                    const el = document.getElementById(nextLinkId);
                                    if (el) el.click();
                                }, 100);
                            }
                        }}>×
                        </button>
                        {(type === 'AUTH') ? fileName : type + ':' + fileName}
                    </a>
                    {
                        <Menu menuId={menuId} visible={this.state.activeContextMenuId === menuId}
                              x={this.state.contextMenuX} y={this.state.contextMenuY} items={[
                            {
                                name: '关闭所有标签页',
                                click: function () {
                                    data.splice(0, data.length);
                                    this.setState({data});
                                }.bind(this)
                            },
                            {
                                name: '关闭其它标签页',
                                click: function () {
                                    data.splice(0, data.length);
                                    data.push(item);
                                    this.setState({data});
                                    setTimeout(function () {
                                        const el = document.getElementById(linkId);
                                        if (el) el.click();
                                    }, 100);
                                }.bind(this)
                            }
                        ]} data={{id: 'tab' + fullPath}}/>
                    }
                </li>
            );
        });
        if (tabs.length === 0) {
            if (welcomePage && welcomePage.length > 0) {
                if (welcomePage === 'none') {
                    return (<div/>);
                } else {
                    return (
                        <iframe frameBorder="0" style={{border: 0, width: '100%', height: '100%'}}
                                src={welcomePage}/>
                    );
                }
            } else {
                return <QuickStart/>;
            }
        } else {
            return (
                <div>
                    <div>
                        <ul className="nav nav-tabs frame-tab-bar" id='fornavframetab_'>
                            {tabs}
                        </ul>
                    </div>
                    <div className="tab-content">
                        {tabContainers}
                    </div>
                </div>
            );
        }
    }
};
