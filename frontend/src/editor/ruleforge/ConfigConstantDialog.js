import * as componentEvent from '../../components/componentEvent.js';
import {OPEN_CONFIG_LIBRARY_DIALOG} from '../../components/dialog/component/ConfigLibraryDialog.jsx';

ruleforge.ConfigConstantDialog = function (parent) {
    this.parent = parent;
};

ruleforge.ConfigConstantDialog.prototype.open = function () {
    componentEvent.eventEmitter.emit(OPEN_CONFIG_LIBRARY_DIALOG, 'constant');
};
