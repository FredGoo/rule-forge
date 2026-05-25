import {renderReact} from '../../components/react-bridge.js';
import SimpleValueWidget from '../../components/widgets/SimpleValueWidget.jsx';

ruleforge.SimpleValue = function (arithmetic, data) {
    this.container = document.createElement("span");
    this.widgetRoot = document.createElement("span");
    this.container.appendChild(this.widgetRoot);
    this.arithmetic = arithmetic;
    if (arithmetic) {
        this.container.appendChild(arithmetic.getContainer());
    }
    this.widgetRef = null;
    renderReact(SimpleValueWidget, {
        initialData: data,
        onDirty: function () { window._setDirty(); },
        ref: function (ref) { this.widgetRef = ref; }.bind(this),
    }, this.widgetRoot);
};

ruleforge.SimpleValue.prototype.getDisplayContainer = function () {
    var container = document.createElement("span");
    container.textContent = this.widgetRef ? this.widgetRef.getDisplayText() : '';
    if (this.arithmetic) {
        var dis = this.arithmetic.getDisplayContainer();
        if (dis) {
            container.appendChild(dis);
        }
    }
    return container;
};

ruleforge.SimpleValue.prototype.initData = function (data) {
    if (!data) {
        return;
    }
    if (this.widgetRef) {
        this.widgetRef.initData(data);
    }
    if (this.arithmetic) {
        this.arithmetic.initData(data["arithmetic"]);
    }
};

ruleforge.SimpleValue.prototype.getValue = function () {
    if (this.widgetRef) {
        return this.widgetRef.getValue();
    }
    return '';
};

ruleforge.SimpleValue.prototype.getContainer = function () {
    return this.container;
};
