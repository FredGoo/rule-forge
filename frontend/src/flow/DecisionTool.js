import CodeMirror from './CodeMirror.js';
import '../../node_modules/codemirror/addon/hint/show-hint.js';
import BaseTool from './BaseTool.js';
import DecisionNode from './DecisionNode.js';
import {MsgBox} from 'flowdesigner';

export default class DecisionTool extends BaseTool {
    getType() {
        return '决策';
    }

    getIcon() {
        return `<i class="rf rf-decision" style="color:#737383"></i>`
    }

    newNode() {
        return new DecisionNode();
    }

    getPropertiesProducer() {
        const _this = this;
        return function () {
            const g = document.createElement('div');
            const decisionTypeGroup = document.createElement('div');
            decisionTypeGroup.className = 'form-group';
            const decisionTypeLabel = document.createElement('label');
            decisionTypeLabel.textContent = '决策类型';
            decisionTypeGroup.appendChild(decisionTypeLabel);
            const decisionTypeSelect = document.createElement('select');
            decisionTypeSelect.className = 'form-control';
            const criteriaOption = document.createElement('option');
            criteriaOption.value = 'Criteria';
            criteriaOption.textContent = '条件';
            decisionTypeSelect.appendChild(criteriaOption);
            const percentOption = document.createElement('option');
            percentOption.value = 'Percent';
            percentOption.textContent = '百分比';
            decisionTypeSelect.appendChild(percentOption);
            const self = this;
            decisionTypeSelect.value = this.decisionType || '';
            decisionTypeGroup.appendChild(decisionTypeSelect);
            const tableContainer = document.createElement('div');
            if (!this.decisionItems || this.decisionItems.length !== self.fromConnections.length) {
                let decisionItems = [...(this.decisionItems ||[])]
                this.decisionItems = [];
                for (let conn of self.fromConnections) {
                    let obj = decisionItems.find(item=>item.to === conn.name)
                    if(!obj) {
                        this.decisionItems.push({connection: '', content: ''});
                    } else {
                        if(this.decisionType === 'Percent') {
                            delete obj.script
                        } else {
                            delete obj.percent
                        }
                        this.decisionItems.push(obj)
                    }
                }
            }

            // Percent table
            const percentTable = document.createElement('table');
            percentTable.className = 'table table-bordered';
            tableContainer.appendChild(percentTable);
            percentTable.style.display = 'none';
            const percentThead = document.createElement('thead');
            percentThead.innerHTML = '<tr><td>百分比(%)</td><td style="width: 160px">流向</td></tr>';
            percentTable.appendChild(percentThead);
            const percentTbody = document.createElement('tbody');
            percentTable.appendChild(percentTbody);
            let index = 0;
            for (let conn of this.fromConnections) {
                const item = this.decisionItems[index];
                index++;
                const tr = document.createElement('tr');
                percentTbody.appendChild(tr);
                const td = document.createElement('td');
                tr.appendChild(td);
                const percentText = document.createElement('input');
                percentText.type = 'text';
                percentText.className = 'form-control';
                td.appendChild(percentText);
                percentText.addEventListener('change', function () {
                    item.percent = this.value;
                });
                percentText.value = item.percent || '';
                const td1 = document.createElement('td');
                tr.appendChild(td1);
                const pathSelect = document.createElement('select');
                pathSelect.className = 'form-control';
                for (let c of this.fromConnections) {
                    const opt = document.createElement('option');
                    opt.textContent = c.name ? c.name : '';
                    pathSelect.appendChild(opt);
                }
                pathSelect.addEventListener('change', function () {
                    item.to = this.value;
                });
                pathSelect.value = item.to || '';
                td1.appendChild(pathSelect);
            }

            // Condition table
            const conditionTable = document.createElement('table');
            conditionTable.className = 'table table-bordered';
            tableContainer.appendChild(conditionTable);
            conditionTable.style.display = 'none';
            const conditionThead = document.createElement('thead');
            conditionThead.innerHTML = '<tr><td>条件脚本</td><td style="width: 160px">流向</td></tr>';
            conditionTable.appendChild(conditionThead);
            const tbody = document.createElement('tbody');
            conditionTable.appendChild(tbody);
            index = 0;
            for (let conn of this.fromConnections) {
                let decisionItem = this.decisionItems[index];
                index++;
                const tr = document.createElement('tr');
                tbody.appendChild(tr);
                const td = document.createElement('td');
                tr.appendChild(td);
                const textGroup = document.createElement('div');
                textGroup.className = 'input-group';
                const scriptText = document.createElement('input');
                scriptText.type = 'text';
                scriptText.className = 'form-control';
                scriptText.style.fontSize = '12px';
                scriptText.value = decisionItem.script || '';
                textGroup.appendChild(scriptText);
                const openEditorSpan = document.createElement('span');
                openEditorSpan.className = 'input-group-btn';
                const openEditorBtn = document.createElement('button');
                openEditorBtn.type = 'button';
                openEditorBtn.className = 'btn btn-default';
                openEditorBtn.innerHTML = '<i class="glyphicon glyphicon-edit"></i>';
                openEditorSpan.appendChild(openEditorBtn);
                textGroup.appendChild(openEditorSpan);
                openEditorBtn.addEventListener('click', function () {
                    const codeEditorContainer = document.createElement('div');
                    const codeEditor = document.createElement('textarea');
                    codeEditor.value = decisionItem.script || '';
                    codeEditorContainer.appendChild(codeEditor);
                    let codeMirror = null;
                    MsgBox.showDialog('编辑脚本', codeEditorContainer, [{
                        name: '确认',
                        click: function click() {
                            if (codeMirror) {
                                const scriptContent = codeMirror.getValue();
                                codeEditor.value = scriptContent;
                                scriptText.value = scriptContent;
                                decisionItem.script = scriptContent;
                            }
                        },
                        holdDialog: false
                    }], [{
                        name: "hide.bs.modal",
                        callback: function () {
                            if (document.querySelectorAll('.CodeMirror-lint-marker-error').length < 1) {
                                return true;
                            } else {
                                alert('语法错误！');
                                codeEditor.value = '';
                                scriptText.value = '';
                                decisionItem.script = '';
                                return false;
                            }
                        }
                    }]);

                    setTimeout(function () {
                        codeMirror = CodeMirror.fromTextArea(codeEditor, {
                            lineNumbers: true,
                            mode: "if",
                            extraKeys: {"Alt-/": "autocomplete"},
                            gutters: ["CodeMirror-linenumbers", "CodeMirror-lint-markers"],
                            lint: {
                                getAnnotations: _this.buildScriptLintFunction('DecisionNode'),
                                async: true
                            }
                        });
                        CodeMirror.commands.autocomplete = function (cm) {
                            cm.showHint({hint: CodeMirror.hint["if"]});
                        };

                        codeMirror.on('change', function (cm, e) {
                            if (e.text == '.') {
                                CodeMirror.commands.autocomplete(cm);
                            }
                        });
                    }, 500);
                });
                td.appendChild(textGroup);

                const td1 = document.createElement('td');
                tr.appendChild(td1);
                const pathSelect = document.createElement('select');
                pathSelect.className = 'form-control';
                pathSelect.style.fontSize = '12px';
                for (let c of this.fromConnections) {
                    const opt = document.createElement('option');
                    opt.textContent = c.name ? c.name : '';
                    pathSelect.appendChild(opt);
                }
                pathSelect.addEventListener('change', function () {
                    decisionItem.to = this.value;
                });
                pathSelect.value = decisionItem.to || '';
                td1.appendChild(pathSelect);
            }

            decisionTypeSelect.addEventListener('change', function () {
                self.decisionType = this.value;
                if (self.decisionType === 'Criteria') {
                    percentTable.style.display = 'none';
                    conditionTable.style.display = '';
                } else {
                    conditionTable.style.display = 'none';
                    percentTable.style.display = '';
                }
            });
            if (self.decisionType === 'Criteria') {
                percentTable.style.display = 'none';
                conditionTable.style.display = '';
            } else if (self.decisionType === 'Percent') {
                conditionTable.style.display = 'none';
                percentTable.style.display = '';
            }
            g.appendChild(decisionTypeGroup);
            g.appendChild(tableContainer);
            g.appendChild(_this.getCommonProperties(this));
            return g;
        }
    }
}
