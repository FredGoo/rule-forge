import '../bootbox.js';
import '../css/iconfont.css';
import '../css/theme.css';
import '../css/theme.css';
import '../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../../node_modules/codemirror/lib/codemirror.css';
import '../../node_modules/bootstrapvalidator/dist/css/bootstrapValidator.css';
import React from 'react';
import { createRoot } from 'react-dom/client';
import {applyMiddleware, createStore} from 'redux';
import {Provider} from 'react-redux';
import * as ACTIONS from './action.js';
import reducer from './reducer.js';
import thunk from 'redux-thunk';
import Tree from '../components/tree/component/Tree.jsx';
import Splitter from '../components/splitter/component/Splitter.jsx';
import FrameTab from '../components/frametab/component/FrameTab.jsx';
import ComponentContainer from './components/ComponentContainer.jsx';
import * as event from './event.js';
import * as componentEvent from '../components/componentEvent.js';
import Loading from '../components/loading/component/Loading.jsx';

document.addEventListener('DOMContentLoaded', function () {
    window._types = null, window._projectName = null, window.componentEvent = componentEvent;
    const store = createStore(reducer, applyMiddleware(thunk));
    store.dispatch(ACTIONS.loadData());
    const documentHeight = document.documentElement.scrollHeight + 'px';
    event.eventEmitter.on(event.CHANGE_CLASSIFY, classify => {
        window._classify = classify;
        if (classify) {
            document.getElementById('__classify_display').innerHTML = '<i class="rf rf-check"/> 分类展示';
            document.getElementById('__no_classify_display').innerHTML = '&nbsp;&nbsp;&nbsp;&nbsp;集中展示';
        } else {
            document.getElementById('__classify_display').innerHTML = '&nbsp;&nbsp;&nbsp;&nbsp;分类展示';
            document.getElementById('__no_classify_display').innerHTML = '<i class="rf rf-check"/> 集中展示';
        }
    });
    event.eventEmitter.on(event.PROJECT_LIST_CHANGE, projectNames => {
        const menu = document.getElementById('__project_filter_menu');
        const menuChildren = Array.from(menu.children);
        menuChildren.forEach(function (li) {
            if (!li.classList.contains('_firstItem')) {
                li.remove();
            } else {
                li.querySelector('a').style.marginLeft = '0px';
            }
        });
        document.getElementById('_show_all_projects_i').classList.add('rf', 'rf-check');
        for (let name of projectNames) {
            const newLi = document.createElement('li');
            newLi.className = 'p_' + name;
            const link = document.createElement('a');
            link.href = '###';
            link.style.marginLeft = '22px';
            link.innerHTML = '<i/> ' + name;
            newLi.appendChild(link);
            menu.appendChild(newLi);

            link.addEventListener('click', function () {
                window._projectName = name;
                componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                setTimeout(function () {
                    store.dispatch(ACTIONS.loadData(window._classify, window._projectName, window._types, window.searchFileName));
                    event.eventEmitter.emit(event.PROJECT_FILTER_CHANGE, name);
                    componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                }, 200);
            });
        }
    });
    event.eventEmitter.on(event.PROJECT_FILTER_CHANGE, name => {
        const menu = document.getElementById('__project_filter_menu');
        const menuChildren = Array.from(menu.children);
        menuChildren.forEach(function (li) {
            const iEl = li.querySelector('i');
            if (iEl) iEl.classList.remove('rf', 'rf-check');
            const aEl = li.querySelector('a');
            if (aEl) aEl.style.marginLeft = '22px';
        });
        const filterLi = menu.querySelector('.p_' + name);
        if (filterLi) {
            const aEl = filterLi.querySelector('a');
            if (aEl) aEl.style.marginLeft = '0px';
            const iEl = filterLi.querySelector('i');
            if (iEl) iEl.classList.add('rf', 'rf-check');
        }
    });

    function searchFile() {
        window.searchFileName = document.querySelector('.fileSearchText').value;
        store.dispatch(ACTIONS.loadData(window._classify, window._projectName, window._types, window.searchFileName));
    }

    createRoot(document.getElementById("container")).render(
        <div>
            <Loading show={true}/>
            <Provider store={store}>
                <Splitter orientation='vertical' position='20%'>
                    <div>
                        <div style={{
                            border: 'solid 1px #ddd',
                            height: '35px',
                            background: '#f5f5f5',
                            padding: '5px 10px'
                        }}>
                            <span className="dropdown" style={{margin: '5px'}}>
                                <a href="#" className="dropdown-toggle" data-toggle="dropdown" title="知识库内容展示方式"><i
                                    className="rf rf-display" style={{fontSize: '12pt'}}/> <b className="caret"/></a>
                                <ul className="dropdown-menu">
                                    <li><a href="#" id="__classify_display" onClick={() => {
                                        componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                        setTimeout(function () {
                                            store.dispatch(ACTIONS.loadData(true, window._projectName, window._types));
                                            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                        }, 200);
                                    }}>✔&nbsp;分类展示</a></li>
                                    <li><a href="#" id="__no_classify_display" onClick={() => {
                                        componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                        setTimeout(function () {
                                            store.dispatch(ACTIONS.loadData(false, window._projectName, window._types));
                                            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                        }, 200);
                                    }}>&nbsp;&nbsp;&nbsp;&nbsp;集中展示</a></li>
                                </ul>
                            </span>

                            <span className="dropdown" style={{margin: '5px'}}>
                                <a href="#" className="dropdown-toggle" data-toggle="dropdown" title="项目过滤"><i
                                    className="rf rf-list" style={{fontSize: '12pt'}}/> <b className="caret"/></a>
                                <ul className="dropdown-menu" id="__project_filter_menu">
                                    <li className="_firstItem">
                                        <a href="#" onClick={function (e) {
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            setTimeout(function () {
                                                store.dispatch(ACTIONS.loadData(window._classify));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            }, 200);
                                            const menu = document.getElementById('__project_filter_menu');
                                            const menuChildren = Array.from(menu.children);
                                            menuChildren.forEach(function (li) {
                                                const iEl = li.querySelector('i');
                                                if (iEl) iEl.classList.remove('rf', 'rf-check');
                                                const aEl = li.querySelector('a');
                                                if (aEl) aEl.style.marginLeft = '22px';
                                            });
                                            e.target.style.marginLeft = '0px';
                                            document.getElementById('_show_all_projects_i').classList.add('rf', 'rf-check');
                                            window._projectName = null;
                                        }}><i id="_show_all_projects_i"/> 显示所有项目
                                        </a>
                                    </li>
                                </ul>
                            </span>

                            <span className="dropdown" style={{margin: '5px'}}>
                                <a href="#" className="dropdown-toggle" data-toggle="dropdown" title="文件类型过滤"><i
                                    className="rf rf-type" style={{fontSize: '12pt'}}/> <b className="caret"/></a>
                                <ul className="dropdown-menu">
                                    <li><a href="#" onClick={function (e) {
                                        componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                        window._types = 'all';
                                        setTimeout(function () {
                                            store.dispatch(ACTIONS.loadData(window._classify, window._projectName, window._types));
                                            componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                        }, 200);
                                        document.querySelectorAll('.filter_file').forEach(function(el) { el.classList.remove('rf', 'rf-check'); });
                                        e.target.querySelector('.filter_file').classList.add('rf', 'rf-check');
                                    }}><i className="filter_file rf rf-check"/> <i className="glyphicon glyphicon-th"/> 显示所有文件</a></li>
                                    <li>
                                        <a href="#" onClick={function (e) {
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            window._types = 'lib';
                                            setTimeout(function () {
                                                store.dispatch(ACTIONS.loadData(window._classify, window._projectName, window._types));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            }, 200);
                                            document.querySelectorAll('.filter_file').forEach(function(el) { el.classList.remove('rf', 'rf-check'); });
                                            e.target.querySelector('.filter_file').classList.add('rf', 'rf-check');
                                        }}><i className="filter_file"/>  <i className="rf rf-library"/> 库文件</a>
                                    </li>
                                    <li>
                                        <a href="#" onClick={function (e) {
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            window._types = 'rule';
                                            setTimeout(function () {
                                                store.dispatch(ACTIONS.loadData(window._classify, window._projectName, window._types));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            }, 200);
                                            document.querySelectorAll('.filter_file').forEach(function(el) { el.classList.remove('rf', 'rf-check'); });
                                            e.target.querySelector('.filter_file').classList.add('rf', 'rf-check');
                                        }}><i className="filter_file"/>  <i className="rf rf-rule"/> 决策集</a>
                                    </li>
                                    <li>
                                        <a href="#" onClick={function (e) {
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            window._types = 'table';
                                            setTimeout(function () {
                                                store.dispatch(ACTIONS.loadData(window._classify, window._projectName, window._types));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            }, 200);
                                            document.querySelectorAll('.filter_file').forEach(function(el) { el.classList.remove('rf', 'rf-check'); });
                                            e.target.querySelector('.filter_file').classList.add('rf', 'rf-check');
                                        }}><i className="filter_file"/> <i className="rf rf-table"/> 决策表</a>
                                    </li>
                                    <li>
                                        <a href="#" onClick={function (e) {
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            window._types = 'tree';
                                            setTimeout(function () {
                                                store.dispatch(ACTIONS.loadData(window._classify, window._projectName, window._types));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            }, 200);
                                            document.querySelectorAll('.filter_file').forEach(function(el) { el.classList.remove('rf', 'rf-check'); });
                                            e.target.querySelector('.filter_file').classList.add('rf', 'rf-check');
                                        }}><i className="filter_file"/> <i className="rf rf-tree"/> 决策树</a>
                                    </li>
                                    <li>
                                        <a href="#" onClick={function (e) {
                                            componentEvent.eventEmitter.emit(componentEvent.SHOW_LOADING);
                                            window._types = 'flow';
                                            setTimeout(function () {
                                                store.dispatch(ACTIONS.loadData(window._classify, window._projectName, window._types));
                                                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
                                            }, 200);
                                            document.querySelectorAll('.filter_file').forEach(function(el) { el.classList.remove('rf', 'rf-check'); });
                                            e.target.querySelector('.filter_file').classList.add('rf', 'rf-check');
                                        }}><i className="filter_file"/> <i className="rf rf-flow"/> 决策流</a>
                                    </li>
                                </ul>
                            </span>

                            <span className="dropdown" style={{margin: '5px'}}>
                                <a href="#" className="dropdown-toggle" data-toggle="dropdown" title="权限配置"><i
                                    className="rf rf-authority" style={{fontSize: '12pt'}}/> <b className="caret"/></a>
                                <ul className="dropdown-menu" id="__authority_config_menu">
                                    <li>
                                        <a href="#" onClick={function (e) {
                                            const url = './html/permission-config-editor.html';
                                            componentEvent.eventEmitter.emit(componentEvent.TREE_NODE_CLICK, {
                                                id: 'security_config_',
                                                name: '资源权限配置',
                                                fullPath: 'security_config_',
                                                path: url
                                            });
                                        }}><i/> 资源权限配置
                                        </a>
                                    </li>
                                </ul>
                            </span>

                            <span style={{float: 'right', margin: '5px 10px'}}>
                                <span style={{color: '#666', marginRight: 10}}>
                                    <i className="glyphicon glyphicon-user"/> {window.__currentUser ? window.__currentUser.username : ''}
                                </span>
                                <a href="#" title="退出登录" onClick={() => {
                                    fetch(window._server + '/frame/logout', {method: 'POST'}).then(function() {
                                        window.location.href = 'html/login.html';
                                    });
                                }}><i className="glyphicon glyphicon-log-out" style={{fontSize: '12pt'}}/></a>
                            </span>
                        </div>
                        <div className='tree' style={{marginLeft: '10px'}}>
                            <div style={{margin: '10px 0px 5px 2px'}}>
                                <input type="text" className="form-control fileSearchText" placeholder="输入要查询的文件名..."
                                       style={{display: 'inline-block', width: '170px'}}/>
                                <a href="#" onClick={searchFile} style={{margin: '6px', fontSize: '16px'}}><i
                                    className="glyphicon glyphicon-search"/></a>
                            </div>
                            <Tree draggable={true} treeType={'public'}/>
                            <Tree draggable={true}/>
                        </div>
                    </div>
                    <div>
                        <ComponentContainer/>
                        <FrameTab welcomePage={window._welcomePage}/>
                    </div>
                </Splitter>
            </Provider>
        </div>,
);

    event.eventEmitter.on(event.EXPAND_TREE_NODE, (nodeData) => {
        const spanEl = document.getElementById('node-' + nodeData.id);
        if (spanEl) {
            const liEl = spanEl.parentElement;
            if (liEl) {
                const parentLi = liEl.closest('li.parent_li');
                if (parentLi) {
                    const liChildren = parentLi.querySelectorAll(':scope > ul > li');
                    liChildren.forEach(function(child) { child.style.display = ''; });
                }
            }
            const firstI = spanEl.querySelector('i:first-child');
            if (firstI) {
                firstI.classList.add('rf-minus');
                firstI.classList.remove('rf-plus');
            }
        }
    })
});

