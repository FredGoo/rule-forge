import React, {Component} from 'react';
import ReactDOM from 'react-dom';
import TreeParentItem from './TreeParentItem.jsx';
import Menu from '../../menu/component/Menu.jsx';
import * as event from '../../componentEvent.js';
import * as ACTIONS from '../../../frame/action.js';

class TreeItem extends Component {
    componentDidMount() {
        const $li = $(ReactDOM.findDOMNode(this));
        const $childrenSpan = $li.children("span");
        const {data, dispatch} = this.props;
        const _this = this;
        $childrenSpan.click(function (e) {
            let $span = $(this);
            if ($li.hasClass("parent_li")) {
                let $liChildren = $span.parent('li.parent_li').find(' > ul > li');
                if ($liChildren.is(":visible")) {
                    $liChildren.hide('fast');
                    $span.children('i:first').addClass('rf-plus').removeClass('rf-minus');
                } else {
                    // 检查是否需要懒加载
                    if (data._needLazyLoad && !data._childrenLoaded) {
                        // 显示加载状态
                        // $span.children('i:first').addClass('rf-loading').removeClass('rf-plus rf-minus');
                        
                        // 调用加载子菜单的action
                        dispatch(ACTIONS.loadChildren(
                            data, 
                            window._classify, 
                            data.name,
                            window._types
                        ));
                    } else if (data.children && data.children.length > 0) {
                        // 正常展开逻辑
                        $liChildren.show('fast');
                        $span.children('i:first').addClass('rf-minus').removeClass('rf-plus');
                    }
                }
                e.stopPropagation();
            }
        });
        if ($li.hasClass("parent_li")) {
            const expandLevel = this.props.expandLevel;
            if (data._level >= expandLevel) {
                var $liChildren = $childrenSpan.parent('li.parent_li').find(' > ul > li');
                $liChildren.hide();
                $childrenSpan.children('i:first').addClass('rf-plus').removeClass('rf-minus');
            } else {
                $childrenSpan.children('i:first').addClass('rf-minus').removeClass('rf-plus');
            }
        }
        this._bindContextMenu(data);
    }

    isFile() {
        const data = this.props.data;
        const name = data.name;
        let isFile = false;
        if (name.indexOf(".") > -1 || name === "ul" || name === 'rp') {
            isFile = true;
        }
        return isFile;
    }

    componentWillUnmount() {
        const data = this.props.data;
        const contextMenu = data.contextMenu;
        if (!contextMenu || contextMenu.length === 0) {
            return;
        }
        $("#node-" + data.id).contextmenu("destroy");
    }

    componentDidUpdate() {
        this._bindContextMenu(this.props.data);
        
        // 检查懒加载是否完成
        // const {data} = this.props;
        // if (data._childrenLoaded && data.children && data.children.length > 0) {
        //     // 使用 setTimeout 确保 DOM 已更新
        //     setTimeout(() => {
        //         const $li = $(ReactDOM.findDOMNode(this));
        //         const $childrenSpan = $li.children("span");
        //         const $liChildren = $childrenSpan.parent('li.parent_li').find(' > ul > li');
                
        //         // 显示子菜单
        //         $liChildren.show('fast');
        //         $childrenSpan.children('i:first').removeClass('rf-loading');
        //     }, 0);
        // } else if (data._childrenLoaded && data.children && data.children.length === 0) {
        //     // 懒加载完成但没有子项
        //     setTimeout(() => {
        //         const $li = $(ReactDOM.findDOMNode(this));
        //         const $childrenSpan = $li.children("span");
        //         $childrenSpan.children('i:first').addClass('rf-plus').removeClass('rf-minus');
        //     }, 0);
        // }
    }

    _bindContextMenu(data) {
        const $node = $("#node-" + data.id);
        $node.contextmenu("destroy");
        const contextMenu = data.contextMenu;
        if (!contextMenu || contextMenu.length === 0) {
            return;
        }
        const menuId = 'treenodemenu' + data.id;
        $node.contextmenu({
            target: '#' + menuId
        });
    }

    render() {
        const {data, dispatch} = this.props;
        const children = data.children;
        const spanId = "node-" + data.id, menuId = 'treenodemenu' + data.id;
        let menu = [];
        if (data.contextMenu) {
            menu.push(<Menu items={data.contextMenu} key={data.id} data={data} dispatch={dispatch} menuId={menuId}/>);
        }
        if (children && children.length > 0) {
            // 确定展开/收起图标
            let expandIcon = 'rf rf-minus';
            if (data._needLazyLoad && !data._childrenLoaded) {
                expandIcon = 'rf rf-plus';
            }
            return (
                <li className='parent_li'>
                    <span id={spanId}>
                        <i className={expandIcon} style={{marginRight: "2px"}}/>
                        <i className={data._icon} style={data._style}/> 
                        <a href='#'style={data._style}> {data.name}</a>
                        <sup><i title={data.lock ? data.lockInfo : ''} className={data.lock ? 'rf rf-lock' : ''}/></sup>
                    </span>
                    {menu}
                    <TreeParentItem dispatch={dispatch} children={children} expandLevel={this.props.expandLevel} treeType={this.props.treeType}/>
                </li>
            );
        } else if (data._needLazyLoad && !data._childrenLoaded) {
            // 需要懒加载但子菜单未加载的情况
            return (
                <li className='parent_li'>
                    <span id={spanId}>
                        <i className='rf rf-plus' style={{marginRight: "2px"}}/>
                        <i className={data._icon} style={data._style}/> 
                        <a href='#' style={data._style}> {data.name}</a>
                        <sup><i title={data.lock ? data.lockInfo : ''}
                                className={data.lock ? 'rf rf-lock' : ''}/></sup>
                    </span>
                    {menu}
                </li>
            );
        } else {
            let isFile = this.isFile();
            return (
                <li>
                    <span id={spanId} onClick={(e) => {
                        if (isFile) {
                            const editorBasePath = this.props.treeType === 'public' ? '/html/resource-editor.html' : data.editorPath;

                            let url = '.' + editorBasePath + "?file=" + data.fullPath;
                            let fullPath = data.fullPath;
                            if (data.type === 'resourcePackage') {
                                const packageName = data.fullPath.split("/")[1];
                                url = '.' + data.editorPath + "?file=" + packageName + '.rp';
                                fullPath = '/' + packageName;
                            }

                            event.eventEmitter.emit(event.TREE_NODE_CLICK, {
                                id: data.id,
                                name: data.name,
                                fullPath: fullPath,
                                path: url,
                                active: true
                            });
                            $('.tree').find('.tree-active').removeClass('tree-active');
                            $("#" + spanId).addClass('tree-active');
                        }
                    }}>
                        <i className={data._icon} style={data._style}/> <a href='#' style={data._style}> {data.name}</a>
                        <sup><i title={data.lock ? data.lockInfo : ''} className={data.lock ? 'rf rf-lock' : ''}/></sup>
                    </span>
                    {menu}
                </li>
            );
        }
    }
}

export default TreeItem;