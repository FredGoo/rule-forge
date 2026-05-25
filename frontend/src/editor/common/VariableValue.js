import {renderReact} from '../../components/react-bridge.js';
import VariableValueWidget from '../../components/widgets/VariableValueWidget.jsx';

ruleforge.VariableValue = function (arithmetic, data, act, functionProperty) {
    this.arithmetic = arithmetic;
    this.container = document.createElement("span");
    this.widgetRoot = document.createElement("span");
    this.container.appendChild(this.widgetRoot);
    this.functionProperty = functionProperty;
    if (arithmetic) {
        this.container.appendChild(arithmetic.getContainer());
    }
    this.widgetRef = null;
    var self = this;
    renderReact(VariableValueWidget, {
        initialData: data,
        libraries: window._ruleforgeEditorVariableLibraries,
        act: act,
        onDirty: function () { window._setDirty(); },
        onFunctionPropertyUpdate: function (variables) {
            if (self.functionProperty && self.functionProperty.initMenu) {
                self.functionProperty.initMenu(variables);
            }
        },
        ref: function (ref) { self.widgetRef = ref; },
    }, this.widgetRoot);
    this.initMenu();
    window._VariableValueArray.push(this);
};

ruleforge.VariableValue.prototype.getDisplayContainer = function () {
    var container = document.createElement("span");
    container.textContent = this.widgetRef ? this.widgetRef.getDisplayLabel() : '';
    if (this.arithmetic) {
        var dis = this.arithmetic.getDisplayContainer();
        if (dis) {
            container.appendChild(dis);
        }
    }
    return container;
};

ruleforge.VariableValue.prototype.initMenu = function (variableLibraries) {
    var data = variableLibraries || window._ruleforgeEditorVariableLibraries;
    if (this.widgetRef && data) {
        this.widgetRef.initMenu(data);
    }
};

ruleforge.VariableValue.prototype.setValue = function (data) {
    if (this.widgetRef) {
        this.widgetRef.setValue(data);
    }
};

ruleforge.VariableValue.prototype.initData = function (data) {
    if (!data) return;
    this.setValue(data);
    if (this.arithmetic) {
        this.arithmetic.initData(data["arithmetic"]);
    }
};

ruleforge.VariableValue.prototype.toXml = function () {
    if (this.widgetRef) {
        return this.widgetRef.toXml();
    }
    return '';
};

ruleforge.VariableValue.prototype.getType = function () {
    if (this.widgetRef) {
        return this.widgetRef.getType();
    }
    return 'VariableCategory';
};

ruleforge.VariableValue.prototype.getContainer = function () {
    return this.container;
};
