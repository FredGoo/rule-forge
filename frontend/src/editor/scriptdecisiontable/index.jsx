/**
 * Created by Jacky.gao on 2016/8/4.
 */
import '../../../node_modules/codemirror/lib/codemirror.css';
import '../../../node_modules/codemirror/addon/hint/show-hint.css';
import '../../../node_modules/codemirror/addon/lint/lint.css';
import '../../../node_modules/bootstrap/dist/css/bootstrap.css';
import './cell.css';
import '../jquery.handsontable.full.min.css';
import '../bootstrap-table.min.css';
import '../context.standalone.css';
import '../../css/iconfont.css';
import '../ruleforge/ruleset.css';

import '../common/RuleForge.js';
import '../common/contextMenu.js';
import '../common/Context.js';

import '../ruleforge/ConfigActionDialog.js';
import '../ruleforge/ConfigConstantDialog.js';
import '../ruleforge/ConfigParameterDialog.js';
import '../ruleforge/ConfigVariableDialog.js';
import '../common/jquery.utils.js';
import '../jquery.handsontable.full.js';
import '../bootstrap-table.min.js';
import './ScriptDecisionTable.js';

import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import React from 'react';
import ReactDOM from 'react-dom';
import {buildProjectNameFromFile, getParameter} from "../../Utils";

$(document).ready(function () {
    const file = getParameter('file');
    window._project = buildProjectNameFromFile(file);

    ReactDOM.render(
        <KnowledgeTreeDialog/>,
        document.getElementById('dialogContainer')
    );
    new RuleForge.DecisionTable('container');
});