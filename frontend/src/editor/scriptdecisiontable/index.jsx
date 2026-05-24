import '../../bootbox.js';
import '../../../node_modules/codemirror/lib/codemirror.css';
import '../../../node_modules/codemirror/addon/hint/show-hint.css';
import '../../../node_modules/codemirror/addon/lint/lint.css';
import '../../../node_modules/bootstrap/dist/css/bootstrap.css';
import './cell.css';
import 'handsontable/styles/handsontable.css';
import '../context.standalone.css';
import '../../css/iconfont.css';
import '../ruleforge/ruleset.css';

import '../common/URule.js';
import '../common/contextMenu.js';
import '../common/Context.js';

import '../ruleforge/ConfigActionDialog.js';
import '../ruleforge/ConfigConstantDialog.js';
import '../ruleforge/ConfigParameterDialog.js';
import '../ruleforge/ConfigVariableDialog.js';
import '../common/jquery.utils.js';
import './ScriptDecisionTable.js';

import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import React from 'react';
import { createRoot } from 'react-dom/client';
import {buildProjectNameFromFile, getParameter} from "../../Utils";

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    window._project = buildProjectNameFromFile(file);

    createRoot(document.getElementById("dialogContainer")).render(
        <KnowledgeTreeDialog/>,
);
    new RuleForge.DecisionTable('container');
});