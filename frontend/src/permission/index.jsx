import '../bootbox.js';
import '../css/iconfont.css';
import '../css/tailwind-base.css';

import React from 'react';
import { createRoot } from 'react-dom/client';
import {Provider} from 'react-redux';
import {applyMiddleware, createStore} from 'redux';
import thunk from 'redux-thunk';
import PermissionConfigEditor from './components/PermissionConfigEditor.jsx';
import reducer from './reducer.js';
import * as action from './action.js';

document.addEventListener('DOMContentLoaded', function () {
    const store = createStore(reducer, applyMiddleware(thunk));
    store.dispatch(action.loadMasterData());
    createRoot(document.getElementById("container")).render(
        <Provider store={store}>
            <PermissionConfigEditor/>
        </Provider>,
);
});
