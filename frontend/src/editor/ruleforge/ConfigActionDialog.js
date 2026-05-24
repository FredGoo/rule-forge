import {MsgBox} from 'flowdesigner';
import * as event from '../../components/componentEvent.js';

ruleforge.ConfigActionDialog = function (parent) {
    this.parent = parent;
    this.init();
};

ruleforge.ConfigActionDialog.prototype.open = function () {
    const _this = this;
    MsgBox.showDialog('动作库配置', this.dialogContent, [
        {
            name: '添加',
            holdDialog: true,
            click: function () {
                event.eventEmitter.emit(event.OPEN_KNOWLEDGE_TREE_DIALOG, {
                    project: window._project,
                    fileType: 'ActionLibrary',
                    callback: function (file, version) {
                        let path = 'jcr:' + file;
                        if (version !== 'LATEST') {
                            path += ':' + version;
                        }
                        const pos = window.actionLibraries.indexOf(path);
                        if (pos !== -1) {
                            MsgBox.alert('动作库文件已存在');
                            return;
                        }
                        _this.tbody.appendChild(_this.newLibRow(path));
                        window.actionLibraries.push(path);
                        window.refreshActionLibraries();
                        window._setDirty();
                    }
                });
            }
        }
    ]);
};

ruleforge.ConfigActionDialog.prototype.init = function () {
    var self = this;
    const table = document.createElement("table");
    table.className = "table table-bordered";
    const thead = document.createElement("thead");
    const headRow = document.createElement("tr");
    const td1 = document.createElement("td");
    td1.textContent = "动作库文件";
    const td2 = document.createElement("td");
    td2.style.width = "70px";
    td2.textContent = "操作";
    headRow.appendChild(td1);
    headRow.appendChild(td2);
    thead.appendChild(headRow);
    table.appendChild(thead);
    this.tbody = document.createElement("tbody");
    table.appendChild(this.tbody);
    this.dialogContent = document.createElement("div");
    this.dialogContent.appendChild(table);

    for (var i = 0; i < window.actionLibraries.length; i++) {
        const lib = window.actionLibraries[i];
        this.tbody.appendChild(this.newLibRow(lib));
    }
};


ruleforge.ConfigActionDialog.prototype.newLibRow = function (lib) {
    const row = document.createElement("tr");
    const libCell = document.createElement("td");
    libCell.textContent = lib;
    row.appendChild(libCell);
    const delCol = document.createElement("td");
    const delButton = document.createElement("button");
    delButton.type = "button";
    delButton.className = "btn btn-link";
    delButton.textContent = "删除";
    delCol.appendChild(delButton);
    delButton.addEventListener('click', function () {
        const pos = window.actionLibraries.indexOf(lib);
        window.actionLibraries.splice(pos, 1);
        row.remove();
        window.refreshActionLibraries();
        window._setDirty();
    });
    row.appendChild(delCol);
    return row;
};