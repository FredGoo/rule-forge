import '../bootbox.js';
import '../css/iconfont.css';
import '../css/tailwind-base.css';

import React from 'react';
import { createRoot } from 'react-dom/client';
import {createStore,applyMiddleware} from 'redux';
import {Provider} from 'react-redux';
import thunk from 'redux-thunk';
import reducer from './reducer.js';
import ParameterEditor from './components/ParameterEditor.jsx';
import * as action from './action.js';
import {getParameter} from '../Utils.js';

document.addEventListener('DOMContentLoaded', function(){
    const store=createStore(reducer,applyMiddleware(thunk));
    const file=getParameter("file");
    store.dispatch(action.loadData(file));
    createRoot(document.getElementById("container")).render(
        <Provider store={store}>
            <ParameterEditor file={file}/>
        </Provider>,
);
});
