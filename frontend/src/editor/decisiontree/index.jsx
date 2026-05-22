/**
 * Created by Jacky.gao on 2016/8/4.
 */
import '../../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../context.standalone.css';
import '../../css/iconfont.css';
import './decision-tree.css';
import '../ruleforge/ruleset.css';
import '../Math.uuid.js';
import '../../Remark.js';
import '../common/contextMenu.js';
import '../common/RuleForge.js';
import '../common/ComparisonOperator.js';
import '../common/ComplexArithmetic.js';
import '../common/VariableValue.js';
import '../common/ResourceListDialog.js';
import '../common/ResourceVersionDialog.js';
import '../common/ConstantValue.js';
import '../ruleforge/ConfigActionDialog.js';
import '../ruleforge/ConfigConstantDialog.js';
import '../ruleforge/ConfigParameterDialog.js';
import '../ruleforge/ConfigVariableDialog.js';
import '../ruleforge/ActionType.js';
import '../ruleforge/PrintAction.js';
import '../ruleforge/AssignmentAction.js';
import '../ruleforge/SimpleArithmetic.js';
import '../common/InputType.js';
import '../common/NextType.js';
import '../common/Paren.js';
import '../common/MethodParameter.js';
import '../common/MethodAction.js';
import '../common/ParameterValue.js';
import '../common/MethodValue.js';
import '../common/FunctionProperty.js';
import '../common/FunctionParameter.js';
import '../common/FunctionValue.js';
import '../common/SimpleValue.js';
import '../ruleforge/NamedReferenceValue.js';
import './ConditionLeft.js';
import '../ruleforge/RuleProperty.js';


import '../common/jquery.utils.js';
import DecisionTree from './new/DecisionTree.js';
import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import QuickTestDialog from '../../components/dialog/component/QuickTestDialog.jsx';
import React from 'react';
import ReactDOM from 'react-dom';
import {buildProjectNameFromFile, getParameter} from "../../Utils";

$(document).ready(function () {
    const file = getParameter('file');
    window._project = buildProjectNameFromFile(file);

    ReactDOM.render(
        <div>
            <KnowledgeTreeDialog/>,
            <QuickTestDialog/>
        </div>,
        document.getElementById('dialogContainer')
    );
    new DecisionTree($('#container'));
});