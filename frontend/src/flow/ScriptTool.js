import CodeMirror from './CodeMirror.js';
import '../../node_modules/codemirror/addon/hint/show-hint.js';
import BaseTool from './BaseTool.js';
import ScriptNode from './ScriptNode.js';

export default class ScriptTool extends BaseTool {
    getType() {
        return '脚本';
    }

    getIcon() {
        return `<i class="rf rf-script" style="color:#737383"></i>`
    }

    newNode() {
        return new ScriptNode();
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
            const scriptGroup = document.createElement('div');
            scriptGroup.className = 'form-group';
            const scriptLabel = document.createElement('label');
            scriptLabel.textContent = '动作脚本';
            scriptGroup.appendChild(scriptLabel);
            const scriptArea = document.createElement('textarea');
            scriptGroup.appendChild(scriptArea);
            const self = this;
            scriptArea.addEventListener('change', function () {
                self.script = this.value;
            });
            scriptArea.value = this.script || '';

            setTimeout(function () {
                const codeMirror = CodeMirror.fromTextArea(scriptArea, {
                    lineNumbers: true,
                    mode: "then",
                    extraKeys: {"Alt-/": "autocomplete"},
                    gutters: ["CodeMirror-linenumbers", "CodeMirror-lint-markers"],
                    lint: {
                        getAnnotations: _this.buildScriptLintFunction('ScriptNode'),
                        async: true
                    }
                });
                CodeMirror.commands.autocomplete = function (cm) {
                    cm.showHint({hint: CodeMirror.hint["if"]});
                };

                codeMirror.on('change', function (cm, e) {
                    if (e.text === '.') {
                        CodeMirror.commands.autocomplete(cm);
                    }
                    const scriptContent = codeMirror.getValue();
                    scriptArea.value = scriptContent;
                    self.script = scriptContent;
                });
            }, 100);

            g.appendChild(scriptGroup);
            g.appendChild(_this.getCommonProperties(this));
            return g;
        }
    }
}
