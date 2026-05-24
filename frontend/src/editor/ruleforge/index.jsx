import '../../bootbox.js';
import '../../../node_modules/bootstrap/dist/css/bootstrap.css';
import '../context.standalone.css';
import './ruleset.css';
import '../../css/iconfont.css';
import '../Math.uuid.js';
import '../common/jquery.utils.js';
import '../common/contextMenu.js';
import '../common/URule.js';
import '../common/Context.js';
import '../../Remark.js';
import './RuleFactory.js';
import './RuleProperty.js';
import '../common/ComparisonOperator.js';
import '../common/ComplexArithmetic.js';
import '../common/VariableValue.js';
import '../common/ResourceListDialog.js';
import '../common/ResourceVersionDialog.js';
import '../common/ConstantValue.js';
import './ConfigActionDialog.js';
import './ConfigConstantDialog.js';
import './ConfigParameterDialog.js';
import './ConfigVariableDialog.js';
import './ActionType.js';
import './SimpleArithmetic.js';
import './PrintAction.js';
import './AssignmentAction.js';
import './Join.js';
import './NamedJoin.js';
import './NamedCondition.js';
import './Connection.js';
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
import './NamedReferenceValue.js';
import './Condition.js';
import './Rule.js';
import './LoopRule.js';

import KnowledgeTreeDialog from '../../components/dialog/component/KnowledgeTreeDialog.jsx';
import QuickTestDialog from '../../components/dialog/component/QuickTestDialog.jsx';
import ResourceVersionDialogComponent from '../common/ResourceVersionDialogComponent.jsx';
import ResourceListDialogComponent from '../common/ResourceListDialogComponent.jsx';
import ReferenceDialog from '../../reference/ReferenceDialog.jsx';
import * as refEvent from '../../reference/event.js';
import React from 'react';
import { createRoot } from 'react-dom/client';
import {buildProjectNameFromFile, getParameter} from "../../Utils";

document.addEventListener('DOMContentLoaded', function () {
    const file = getParameter('file');
    window._project = buildProjectNameFromFile(file);

    // 设置全局引用事件
    window.refEvent = refEvent;

    const container = document.getElementById('container');
    new RuleFactory(container);
    const dialogContainer = document.createElement("div");
    container.appendChild(dialogContainer);
    const root = createRoot(dialogContainer);
    root.render(
        <div>
            <KnowledgeTreeDialog/>
            <ReferenceDialog/>
            <QuickTestDialog/>
            <ResourceVersionDialogComponent/>
            <ResourceListDialogComponent/>
        </div>
    );
});