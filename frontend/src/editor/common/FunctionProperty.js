import {renderReact} from '../../components/react-bridge.js';
import FunctionPropertyWidget from '../../components/widgets/FunctionPropertyWidget.jsx';

ruleforge.FunctionProperty = function () {
    this.container = document.createElement("span");
    this.widgetRoot = document.createElement("span");
    this.container.appendChild(this.widgetRoot);
    this.widgetRef = null;
    renderReact(FunctionPropertyWidget, {
        onDirty: function () { window._setDirty(); },
        ref: function (ref) { this.widgetRef = ref; }.bind(this),
    }, this.widgetRoot);
};

ruleforge.FunctionProperty.prototype.toXml = function () {
    if (this.widgetRef) {
        return this.widgetRef.toXml();
    }
    return '';
};

ruleforge.FunctionProperty.prototype.initMenu = function (data) {
    if (this.widgetRef) {
        this.widgetRef.initMenu(data);
    }
};

ruleforge.FunctionProperty.prototype.setProperty = function (data) {
    if (this.widgetRef) {
        this.widgetRef.setProperty(data);
    }
};

ruleforge.FunctionProperty.prototype.getContainer = function () {
    return this.container;
};
