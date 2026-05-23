/**
 * Created by jacky on 2016/6/17.
 */
import '../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../../node_modules/bootstrapvalidator/dist/css/bootstrapValidator.css';
import React from 'react';
import { createRoot } from 'react-dom/client';
import {applyMiddleware, createStore} from 'redux';
import {Provider} from 'react-redux';
import thunk from 'redux-thunk';
import reducer from './reducer.js';
import PackageEditor from './components/PackageEditor.jsx';
import * as action from './action.js';

$(document).ready(function () {
    const store = createStore(reducer, applyMiddleware(thunk));
    const project = _getParameter("file").replace('.rp', '');
    store.dispatch(action.loadMasterData(project));
    store.dispatch(action.loadPackageConfig(project));
    createRoot(document.getElementById("container")).render(
        <Provider store={store}>
            <PackageEditor project={project}/>
        </Provider>,
);
});

function _getParameter(name) {
    var reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)");
    var r = window.location.search.substr(1).match(reg);
    if (r != null) return unescape(r[2]);
    return null;
}