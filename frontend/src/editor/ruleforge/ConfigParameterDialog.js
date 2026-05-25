import * as componentEvent from '../../components/componentEvent.js';
import {OPEN_CONFIG_LIBRARY_DIALOG} from '../../components/dialog/component/ConfigLibraryDialog.jsx';

ruleforge.ConfigParameterDialog = function (parent) {
    this.parent = parent;
};

ruleforge.ConfigParameterDialog.prototype.open = function () {
    componentEvent.eventEmitter.emit(OPEN_CONFIG_LIBRARY_DIALOG, 'parameter');
};
