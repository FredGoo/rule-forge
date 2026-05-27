import '@/bootbox.js';
import '@/css/iconfont.css';
import 'codemirror/lib/codemirror.css';
import 'bootstrapvalidator/dist/css/bootstrapValidator.css';
import '../css/tailwind-base.css';
import React from 'react';
import {createRoot} from 'react-dom/client';
import {applyMiddleware, createStore} from 'redux';
import {Provider} from 'react-redux';
import * as ACTIONS from '@/frame/action.js';
import reducer from '@/frame/reducer.js';
import thunk from 'redux-thunk';
import Splitter from '@/components/splitter/component/Splitter.jsx';
import FrameTab from '@/components/frametab/component/FrameTab.jsx';
import ComponentContainer from '@/frame/components/ComponentContainer.jsx';
import TopBar from '@/frame/components/TopBar.jsx';
import FileTreePanel from '@/frame/components/FileTreePanel.jsx';
import Loading from '@/components/loading/component/Loading.jsx';
import * as event from '@/frame/event.js';
import * as componentEvent from '@/components/componentEvent.js';

document.addEventListener('DOMContentLoaded', function () {
    window._types = null;
    window._projectName = null;
    window.componentEvent = componentEvent;
    const store = createStore(reducer, applyMiddleware(thunk));
    store.dispatch(ACTIONS.loadData());

    var topBarRef = null;
    var frameTabRef = null;

    createRoot(document.getElementById("container")).render(
        <div className="app-layout">
            <Loading show={true}/>
            <Provider store={store}>
                <TopBar ref={ref => { topBarRef = ref; }}
                        store={store} eventObj={event}
                        frameTabRef={frameTabRef}/>
                <div className="app-body">
                    <Splitter orientation='vertical' position='260px'>
                        <FileTreePanel store={store}/>
                        <div className="app-content">
                            <ComponentContainer/>
                            <FrameTab ref={ref => {
                                frameTabRef = ref;
                                if (topBarRef) topBarRef.frameTabRef = ref;
                            }}
                                      welcomePage={window._welcomePage}
                                      onTabsChange={(tabs, activeTab) => {
                                          if (topBarRef) topBarRef.setTabData(tabs, activeTab);
                                      }}/>
                        </div>
                    </Splitter>
                </div>
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
    });
});
