import {renderReact} from '../../components/react-bridge.js';
import ParameterValueWidget from '../../components/widgets/ParameterValueWidget.jsx';

ruleforge.ParameterValue = function (arithmetic, data, act) {
    this.arithmetic = arithmetic;
    this.container = document.createElement("span");
    this.widgetRoot = document.createElement("span");
    this.container.appendChild(this.widgetRoot);
    if (arithmetic) {
        this.container.appendChild(arithmetic.getContainer());
    }
    this.widgetRef = null;
    renderReact(ParameterValueWidget, {
        initialData: data,
        libraries: window._ruleforgeEditorParameterLibraries,
        act: act,
        onDirty: function () { window._setDirty(); },
        ref: function (ref) { this.widgetRef = ref; }.bind(this),
    }, this.widgetRoot);
    this.initMenu();
    window._ParameterValueArray.push(this);
};

ruleforge.ParameterValue.prototype.getDisplayContainer = function () {
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

ruleforge.ParameterValue.prototype.initMenu = function (parameterLibraries) {
    var data = parameterLibraries || window._ruleforgeEditorParameterLibraries;
    if (this.widgetRef && data) {
        this.widgetRef.initMenu(data);
    }
};

ruleforge.ParameterValue.prototype.setValue = function (data) {
    if (this.widgetRef) {
        this.widgetRef.setValue(data);
    }
};

ruleforge.ParameterValue.prototype.initData = function (data) {
    if (!data) return;
    this.setValue(data);
    if (this.arithmetic) {
        this.arithmetic.initData(data["arithmetic"]);
    }
};

ruleforge.ParameterValue.prototype.toXml = function () {
    if (this.widgetRef) {
        return this.widgetRef.toXml();
    }
    return '';
};

ruleforge.ParameterValue.prototype.getContainer = function () {
    return this.container;
};
