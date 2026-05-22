import {ajaxSave, getParameter} from '../../Utils.js';
import {MsgBox} from 'flowdesigner';
import {saveNewVersion} from "../../Utils";
import * as event from '../../components/componentEvent.js';

(function ($) {
    $.fn.ruleforge = function () {
        // this.on('click', function(e) {
        //     e.stopImmediatePropagation();
        //     e.preventDefault();
        //     return false;
        // }).css("pointer-events", "none");
        
        this._dirty = false;
        this.rules = [];
        var file = getParameter("file");
        var version = getParameter("version") || "";
        console.log('当前版本号', version)
        if (!file || file.length < 1) {
            MsgBox.alert("当前编辑器未指定具体规则文件！");
            return;
        }
        var saveButton = '<div class="btn-group btn-group-sm navbar-btn" style="margin-top:0;margin-bottom: 0" role="group" aria-label="...">' +
            '<button id="saveButton" type="button" class="btn btn-default navbar-btn" ><i class="icon-save"/> 保存</button>' +
            '<button id="saveButtonNewVersion" type="button" class="btn btn-default navbar-btn"><i class="icon-save"/> 生成版本</button>' +
            '</div>';
        var testButton = '<div class="btn-group btn-group-sm navbar-btn" style="margin-left:5px;margin-top:0;margin-bottom: 0" role="group" aria-label="...">' +
            '<button id="testButton" type="button" class="btn btn-success navbar-btn"><i class="glyphicon glyphicon-flash"/> 快速测试</button>' +
            '</div>';
        var referenceButton = '<div class="btn-group btn-group-sm navbar-btn" style="margin-left:5px;margin-top:0;margin-bottom: 0" role="group" aria-label="...">' +
            '<button id="referenceButton" type="button" class="btn btn-info navbar-btn"><i class="rf rf-link"/> 查看引用</button>' +
            '</div>';
        var toolbarHtml = `<nav class="navbar navbar-default" style="margin: 5px">
        	<div style="margin-left:5px;margin-top:0;margin-bottom: 0">
	            <div>
	                <button id="addRuleButton" type="button" class="btn btn-default btn-sm navbar-btn"><i class="glyphicon glyphicon-plus-sign"/> 添加规则</button>
	                <button id="addLoopRuleButton" type="button" class="btn btn-default btn-sm navbar-btn"><i class="glyphicon glyphicon-plus"/> 添加循环规则</button>
	                <div class="btn-group btn-group-sm navbar-btn" style="margin-left:5px;margin-top:0;margin-bottom: 0" role="group" aria-label="...">
	                    <button id="configVarButton" type="button" class="btn btn-default"><i class="rf rf-variable"/> 变量库</button>
	                    <button id="configConstantsButton" type="button" class="btn btn-default"><i class="rf rf-constant"/> 常量库</button>
	                    <button id="configActionButton" type="button" class="btn btn-default"><i class="rf rf-action"/> 动作库</button>
	                    <button id="configParameterButton" type="button" class="btn btn-default"><i class="rf rf-parameter"/> 参数库</button>
	                </div>
	                ${saveButton}
	                ${testButton}
	                ${referenceButton}
	            </div>
            </div>
    	</nav>`;
        var toolbar = $(toolbarHtml);
        toolbar.css({
            diaplay: "inline-block"
        });
        this.append(toolbar);
        var self = this;

        $("#addRuleButton").click(function () {
            var ruleKey = prompt("规则编号", "");
            if (ruleKey == null || ruleKey === '') {
                var rule = _addRule();
                rule.initTopJoin();
            } else {
                var url = window._server + '/common/findRuleByKey';
                var projectName = file.split('/')[1];

                $.ajax({
                    url: url,
                    type: 'POST',
                    data: {
                        ruleKey: ruleKey,
                        projectName: projectName
                    },
                    success: function (res) {
                        if (res != null && res.length > 0) {
                            _addRule(res[0]);
                        } else {
                            var rule = _addRule();
                            rule.initTopJoin();
                        }
                    }
                })
            }
        });

        $("#addLoopRuleButton").click(function () {
            var rule = _addLoopRule();
            rule.initTopJoin();
        });

        $("#configVarButton").click(function () {
            if (!self.configVarDialog) {
                self.configVarDialog = new ruleforge.ConfigVariableDialog(self);
            }
            self.configVarDialog.open();
        });

        $("#configConstantsButton").click(function () {
            if (!self.configConstantDialog) {
                self.configConstantDialog = new ruleforge.ConfigConstantDialog(self);
            }
            self.configConstantDialog.open();
        });

        $("#configActionButton").click(function () {
            if (!self.configActionDialog) {
                self.configActionDialog = new ruleforge.ConfigActionDialog(self);
            }
            self.configActionDialog.open();
        });

        $("#configParameterButton").click(function () {
            if (!self.configParameterDialog) {
                self.configParameterDialog = new ruleforge.ConfigParameterDialog(self);
            }
            self.configParameterDialog.open();
        });

        $("#testButton").click(function() {
            let decodedFile = decodeURIComponent(file)
            event.eventEmitter.emit(event.OPEN_QUICK_TEST_DIALOG, {project: window._project, file: decodedFile, type: 'ruleLib'});
        })

        $("#referenceButton").click(function () {
            // 解码文件路径，确保传递的是解码后的路径
            const decodedFile = decodeURIComponent(file);
            const title = `规则集"${decodedFile}"`;
            const data = {
                path: decodedFile
            };
            // 使用全局的引用事件
            if (window.refEvent) {
                window.refEvent.eventEmitter.emit(window.refEvent.OPEN_REFERENCE_DIALOG, data, title);
            }
        });

        const saveButtonNewVersion = $("#saveButtonNewVersion");
        saveButtonNewVersion.click(function () {
            save(true);
        });
        // saveButtonNewVersion.addClass("disabled");
        const saveButtonNotNew = $("#saveButton");
        saveButtonNotNew.click(function () {
            save(false);
        });
        saveButtonNotNew.addClass("disabled");

        var remarkContainer = $("<div style='margin: 5px;padding: 5px;'></div>");
        this.append(remarkContainer);
        this.remark = new Remark(remarkContainer);

        _loadRulesetFileData();

        var _this = this;
        this.sortable({
            delay: 200,
            update: function (event, ui) {
                var children = _this.children("div");
                children.each(function (index, div) {
                    let item = $(div), id = item.prop("id"), rules = _this.rules, targetRule = null;
                    for (let rule of rules) {
                        if (rule.uuid === id) {
                            targetRule = rule;
                            break;
                        }
                    }
                    if (targetRule) {
                        const pos = rules.indexOf(targetRule);
                        rules.splice(pos, 1);
                        rules.splice(index, 0, targetRule);
                    }
                });
                window._setDirty();
            }
        });

        function save(newVersion) {
            if (!newVersion && saveButtonNotNew.hasClass("disabled")) {
                return false;
            }
            var xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
            xml += "<rule-set>";
            $.each(parameterLibraries, function (index, item) {
                xml += "<import-parameter-library path=\"" + item + "\"/>";
            });
            $.each(variableLibraries, function (index, item) {
                xml += "<import-variable-library path=\"" + item + "\"/>";
            });
            $.each(constantLibraries, function (index, item) {
                xml += "<import-constant-library path=\"" + item + "\"/>";
            });
            $.each(actionLibraries, function (index, item) {
                xml += "<import-action-library path=\"" + item + "\"/>";
            });
            xml += self.remark.toXml();
            try {
                for (var i = 0; i < self.rules.length; i++) {
                    var rule = self.rules[i];
                    xml += rule.toXml();
                }
            } catch (error) {
                MsgBox.alert(error);
                return;
            }
            xml += "</rule-set>";
            xml = encodeURIComponent(xml);
            let postData = {content: xml, file, newVersion};
            const url = window._server + '/common/saveFile';
            if (newVersion) {
                saveNewVersion(url, postData, function () {
                    cancelDirty();
                    bootbox.alert('保存成功!');
                });
            } else {
                ajaxSave(url, postData, function () {
                    cancelDirty();
                    bootbox.alert('保存成功!');
                })
            }
        }

        window._setDirty = function () {
            if (self._dirty) {
                return;
            }
            self._dirty = true;
            window._dirty = true;
            saveButtonNotNew.html("<i class='rf rf-save'/> *保存");
            saveButtonNotNew.removeClass("disabled");
        };

        function cancelDirty() {
            if (!self._dirty) {
                return;
            }
            self._dirty = false;
            window._dirty = false;
            saveButtonNotNew.html("<i class='rf rf-save'/> 保存");
            saveButtonNotNew.addClass("disabled");
        }

        function _addRule(data) {
            var ruleContainer = $("<div class='well' style='margin:5px;padding:8px;background-color: #fdfdfd'></div>");
            self.append(ruleContainer);
            var rule = new ruleforge.Rule(self, ruleContainer, data);
            self.rules.push(rule);
            window._setDirty();
            return rule;
        }

        function _addLoopRule(data) {
            var ruleContainer = $("<div class='well' style='margin:5px;padding:8px;border-color:#337AB7;background-color: #fdfdfd'></div>");
            self.append(ruleContainer);
            var rule = new ruleforge.LoopRule(self, ruleContainer, data);
            self.rules.push(rule);
            window._setDirty();
            return rule;
        }

        function _loadRulesetFileData() {
            var url = window._server + '/common/loadXml';
            $.ajax({
                url: url,
                type: 'POST',
                data: {files: file},
                error: function (response) {
                    if (response && response.responseText) {
                        bootbox.alert("<span style='color: red'>加载文件失败，服务端错误：" + response.responseText + "</span>");
                    } else {
                        bootbox.alert("<span style='color: red'>加载文件失败,服务端出错</span>");
                    }
                },
                success: function (data) {
                    var ruleset = data[0];
                    var libraries = ruleset["libraries"];
                    self.remark.setData(ruleset["remark"]);
                    if (libraries) {
                        for (var i = 0; i < libraries.length; i++) {
                            var lib = libraries[i];
                            var type = lib["type"];
                            var path = lib["path"];
                            switch (type) {
                                case "Constant":
                                    constantLibraries.push(path);
                                    break;
                                case "Action":
                                    actionLibraries.push(path);
                                    break;
                                case "Variable":
                                    variableLibraries.push(path);
                                    break;
                                case "Parameter":
                                    parameterLibraries.push(path);
                                    break;
                            }
                        }
                    }
                    refreshActionLibraries();
                    refreshConstantLibraries();
                    refreshVariableLibraries();
                    refreshParameterLibraries();
                    refreshFunctionLibraries();
                    var rules = ruleset["rules"];
                    for (var i = 0; i < rules.length; i++) {
                        var rule = rules[i];
                        if (rule.loopRule) {
                            _addLoopRule(rule);
                        } else {
                            _addRule(rule);
                        }
                    }
                    cancelDirty();
                }
            });
        }

    };
})(jQuery);
