import {renderReact} from '../../components/react-bridge.js';
import ConstantValueWidget from '../../components/widgets/ConstantValueWidget.jsx';

ruleforge.ConstantValue = function (arithmetic, data) {
    this.arithmetic = arithmetic;
    this.container = document.createElement("span");
    this.widgetRoot = document.createElement("span");
    this.container.appendChild(this.widgetRoot);
    if (arithmetic) {
        this.container.appendChild(arithmetic.getContainer());
    }
    this.widgetRef = null;
    renderReact(ConstantValueWidget, {
        initialData: data,
        libraries: window._ruleforgeEditorConstantLibraries,
        onDirty: function () { window._setDirty(); },
        ref: function (ref) { this.widgetRef = ref; }.bind(this),
    }, this.widgetRoot);
    this.initMenu();
    window._ConstantValueArray.push(this);
};

ruleforge.ConstantValue.prototype.initMenu = function (constantLibraries) {
    var data = constantLibraries || window._ruleforgeEditorConstantLibraries;
    if (this.widgetRef && data) {
        this.widgetRef.initMenu(data);
    }
};

ruleforge.ConstantValue.prototype.getDisplayContainer = function () {
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

ruleforge.ConstantValue.prototype.toXml = function () {
    if (this.widgetRef) {
        return this.widgetRef.toXml();
    }
    return '';
};

ruleforge.ConstantValue.prototype.getContainer = function () {
    return this.container;
};
