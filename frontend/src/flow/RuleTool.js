import BaseTool from './BaseTool.js';
import RuleNode from './RuleNode.js';
import * as event from '../components/componentEvent.js';
import {MsgBox} from "flowdesigner"

export default class RuleTool extends BaseTool {
    getType() {
        return '规则';
    }

    getIcon() {
        return `<i class="rf rf-rule" style="color:#737383"></i>`
    }

    newNode() {
        return new RuleNode();
    }

    getConfigs() {
        return {
            out: 1
        };
    }

    getPropertiesProducer() {
        const _this = this;
        return function () {
            const g = document.createElement('div');
            const fileGroup = document.createElement('div');
            fileGroup.className = 'form-group';
            const fileLabel = document.createElement('label');
            fileLabel.textContent = '目标规则文件';
            fileGroup.appendChild(fileLabel);
            const fileInputGroup = document.createElement('div');
            fileInputGroup.className = 'input-group';
            fileGroup.appendChild(fileInputGroup);
            const fileText = document.createElement('input');
            fileText.type = 'text';
            fileText.disabled = true;
            fileText.className = 'form-control';
            const self = this;
            fileInputGroup.appendChild(fileText);
            fileText.addEventListener('change', function () {
                self.file = this.value;
            });
            fileText.value = this.file || '';

            const fileBtnSpan = document.createElement('span');
            fileBtnSpan.className = 'input-group-btn';
            fileBtnSpan.title = '选择目标文件';
            const fileBtn = document.createElement('button');
            fileBtn.className = 'btn btn-default';
            fileBtn.innerHTML = '<i class="glyphicon glyphicon-search"/>';
            fileBtnSpan.appendChild(fileBtn);
            fileInputGroup.appendChild(fileBtnSpan);

            const openFileBtnSpan = document.createElement('span');
            openFileBtnSpan.className = 'input-group-btn';
            openFileBtnSpan.title = '打开这个文件';
            const openFileBtn = document.createElement('button');
            openFileBtn.className = 'btn btn-default';
            openFileBtn.innerHTML = '<i class="glyphicon glyphicon glyphicon-folder-open"/>';
            openFileBtnSpan.appendChild(openFileBtn);
            fileInputGroup.appendChild(openFileBtnSpan);

            if (!this.file || this.file === '') {
                openFileBtn.classList.add("disabled");
            }
            let versionText;
            fileBtn.addEventListener('click', function () {
                event.eventEmitter.emit(event.OPEN_KNOWLEDGE_TREE_DIALOG, {
                    project: window._project,
                    callback: function (file, version) {
                        file = 'jcr:' + file;
                        self.file = file;
                        self.version = version;
                        fileText.value = file;
                        versionText.value = version;
                        openFileBtn.classList.remove("disabled");
                    }
                });
            });
            openFileBtn.addEventListener('click', function () {
                if (!self.file || self.file === '') {
                    MsgBox.alert("请先选择文件");
                    return;
                }
                let fileName = self.file;
                if (fileName.indexOf("jcr:") > -1) {
                    fileName = fileName.substring(4, fileName.length);
                }
                const pos = fileName.indexOf(".") + 1;
                const extName = fileName.substring(pos, fileName.length);
                let editorPath = './html';
                if (extName === 'rs.xml') {
                    editorPath += "/ruleset-editor.html";
                } else if (extName === 'dt.xml') {
                    editorPath += "/decision-table-editor.html";
                } else if (extName === 'dtree.xml') {
                    editorPath += "/decision-tree-editor.html";
                } else if (extName === 'ul') {
                    editorPath += "/ul-editor.html";
                } else if (extName === 'sc') {
                    editorPath += "/score-card-editor.html";
                } else if (extName === 'rl.xml') {
                    editorPath += "/rule-flow-designer.html";
                } else if (extName === 'scc.xml') {
                    editorPath += "/complexscorecard-editor.html";
                }
                if (editorPath === window._server) {
                    MsgBox.alert("无法打开文件[" + self.file + "]");
                    return;
                }
                let fullPath = fileName;
                if (self.version && self.version !== "LATEST") {
                    fullPath += ":" + self.version;
                }
                editorPath += "?file=" + fullPath;
                const componentEvent = window.parent.componentEvent;
                if (componentEvent) {
                    componentEvent.eventEmitter.emit("tree_node_click", {
                        fullPath,
                        name: fileName,
                        id: fullPath,
                        path: editorPath,
                        active: true
                    });
                }
            });
            g.appendChild(fileGroup);
            const versionGroup = document.createElement('div');
            versionGroup.className = 'form-group';
            const versionLabel = document.createElement('label');
            versionLabel.textContent = '文件版本';
            versionGroup.appendChild(versionLabel);
            versionText = document.createElement('input');
            versionText.type = 'text';
            versionText.disabled = true;
            versionText.className = 'form-control';
            versionGroup.appendChild(versionText);
            g.appendChild(versionGroup);
            versionText.value = this.version || '';
            g.appendChild(_this.getCommonProperties(this));
            return g;
        }
    }
}
