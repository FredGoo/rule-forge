import '../bootbox.js';
import '../css/iconfont.css';
import '../css/tailwind-base.css';
import React from 'react';
import { createRoot } from 'react-dom/client';
import {applyMiddleware, createStore} from 'redux';
import {Provider} from 'react-redux';
import thunk from 'redux-thunk';

import reducer from './reducer.js';
import ClientConfigEditor from './component/ClientConfigEditor.jsx';
import * as action from './action.js';
import {getParameter} from '../Utils.js';

document.addEventListener('DOMContentLoaded', function () {
    const store = createStore(reducer, applyMiddleware(thunk));
    const project = getParameter('project');
    store.dispatch(action.loadData(project));
    createRoot(document.getElementById("container")).render(
        <Provider store={store}>
            <ClientConfigEditor project={project}/>
        </Provider>,
);
});
