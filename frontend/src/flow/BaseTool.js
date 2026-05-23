import {Tool} from 'flowdesigner';
import '../../node_modules/codemirror/addon/lint/lint.js';

export default class BaseTool extends Tool {
    getCommonProperties(target) {
        const eventGroup = $(`<div class="form-group" title="一个实现了com.ruleforge.model.flow.NodeEvent接口配置在Spring中bean的id，一旦配置在流程进入及离开该节点时会触发这个实现类"><label>事件Bean</label></div>`);
        const eventText = $(`<input type="text" class="form-control">`);
        eventText.change(function () {
            target.eventBean = $(this).val();
        });
        eventText.val(target.eventBean);
        eventGroup.append(eventText);
        return eventGroup;
    }

    buildScriptLintFunction(type) {
        return function (text, updateLinting, options, editor) {
            if (text === '') {
                updateLinting(editor, []);
                return;
            }
            const url = window._server + '/common/scriptValidation';
            fetch(url, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: new URLSearchParams({type, content: text}).toString()
            }).then(function(response) {
                if (!response.ok) throw response;
                return response.json();
            }).then(function (result) {
                if (result) {
                    for (let item of result) {
                        item.from = {line: item.line - 1};
                        item.to = {line: item.line - 1};
                    }
                    updateLinting(editor, result);
                } else {
                    updateLinting(editor, []);
                }
            }).catch(function () {
                alert('语法检查操作失败！');
            });
        };
    }
}