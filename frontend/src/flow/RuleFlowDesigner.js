import {Event, FlowDesigner, MsgBox, Node} from 'flowdesigner';
import BaseNode from './BaseNode.js';
import * as event from '../components/componentEvent.js';
import * as action from '../frame/action.js';
import * as CONSTANTS from './Constants.js';


export default class RuleFlowDesigner extends FlowDesigner {
    constructor(containerId) {
        super(containerId);
        this.importVariableLibraries = [];
        this.importConstantLibraries = [];
        this.importParameterLibraries = [];
        this.importActionLibraries = [];
        this.variableLibraries = [];
        this.constantLibraries = [];
        this.parameterLibraries = [];
        this.actionLibraries = [];
        this.flowId = '';
    }
    toXML() {
        if (!this.flowId || this.flowId.length < 1) {
            MsgBox.alert('决策流ID必须指定!');
            return;
        }
        if (!this.validate()) {
            return;
        }
        if (this.toJSON().length < 2) {
            MsgBox.alert('决策流至少要包含一个开始节点和一个其它类型节点!');
            return;
        }
        let debug = false;
        if (this.debug !== undefined && this.debug !== null) {
            debug = this.debug;
        }
        let xml = '<?xml version="1.0" encoding="utf-8"?>';
        xml += `<rule-flow id="${this.flowId}" debug="${debug}">`;
        for (let lib of this.importVariableLibraries) {
            xml += `<import-variable-library path="${lib}"/>`;
        }
        for (let lib of this.importConstantLibraries) {
            xml += `<import-constant-library path="${lib}"/>`;
        }
        for (let lib of this.importParameterLibraries) {
            xml += `<import-parameter-library path="${lib}"/>`;
        }
        for (let lib of this.importActionLibraries) {
            xml += `<import-action-library path="${lib}"/>`;
        }

        // 1. 收集所有规则包节点的 name -> 第一个 rule.name 的映射
        var rulesPackageEntryMap = {};
        for (var i = 0; i < this.context.allFigures.length; i++) {
            var figure = this.context.allFigures[i];
            if (figure.constructor && figure.constructor.name === 'RulesPackageNode' && figure.data.rulesList && figure.data.rulesList.length > 0) {
                rulesPackageEntryMap[figure.name] = figure.data.rulesList[0].name;
            }
        }
        this._rulesPackageEntryMap = rulesPackageEntryMap;

        // 2. 遍历所有节点，赋值映射表
        for (var i = 0; i < this.context.allFigures.length; i++) {
            var figure = this.context.allFigures[i];
            if (!(figure instanceof BaseNode)) {
                continue;
            }
            figure._rulesPackageEntryMap = rulesPackageEntryMap;
            xml += figure.toXML();
        }
        delete this._rulesPackageEntryMap;
        xml += '</rule-flow>';
        xml = encodeURIComponent(xml);
        return xml;
    }

    fromJson(json) {
        // 检查节点类型和合并条件
        var hasRulesPackageNodes = false;
        var hasPackageRules = false;
        
        if (json.nodes && Array.isArray(json.nodes)) {
            for (var i = 0; i < json.nodes.length; i++) {
                var node = json.nodes[i];
                // 检查是否有规则包节点
                if (node.type === 'RulesPackage' || node.type === '规则包') {
                    hasRulesPackageNodes = true;
                }
                // 检查是否有需要合并的规则节点
                if ((node.type === 'Rule' || node.type === '规则') && node.packageName) {
                    hasPackageRules = true;
                }
            }
        }
        
        // 如果已经有规则包节点，直接跳过合并逻辑
        if (hasRulesPackageNodes) {
            console.log('检测到已有规则包节点，跳过合并逻辑');
        } else if (hasPackageRules) {
            // 只有在有规则包规则时才进行合并
            // 2. 按照 packageName 和 index 顺序合并规则节点为规则包
            if (json.nodes && Array.isArray(json.nodes)) {
                // 收集所有需要合并的规则节点
                var packageGroups = {};
                var standaloneRules = [];
                
                for (var i = 0; i < json.nodes.length; i++) {
                    var node = json.nodes[i];
                    if (node.type === 'Rule' || node.type === '规则') {
                        if (node.packageName) {
                            // 有 packageName 的规则，按 packageName 分组
                            if (!packageGroups[node.packageName]) {
                                packageGroups[node.packageName] = [];
                            }
                            packageGroups[node.packageName].push(node);
                        } else {
                            // 没有 packageName 的规则，保持独立
                            standaloneRules.push(node);
                        }
                    }
                }
            
                // 对每个 packageName 组内的规则按 index 排序
                for (var packageName in packageGroups) {
                    packageGroups[packageName].sort(function(a, b) {
                        var indexA = parseInt(a.index) || 0;
                        var indexB = parseInt(b.index) || 0;
                        return indexA - indexB;
                    });
                }
                
                // 创建合并后的规则包节点
                var mergedPackages = [];
                var packageIndex = 0;
                for (var packageName in packageGroups) {
                    var rules = packageGroups[packageName];
                    if (rules.length > 0) {
                        // 优先查找是否已存在同名规则包节点（用户拖动后的位置）
                        var existingPackageNode = null;
                        for (var i = 0; i < json.nodes.length; i++) {
                            var node = json.nodes[i];
                            if (node.name === packageName && (node.type === 'RulesPackage' || node.type === '规则包')) {
                                existingPackageNode = node;
                                break;
                            }
                        }
                
                        // 如果存在已保存的规则包节点，使用其坐标；否则使用第一个规则的坐标
                        var packageX, packageY;
                        if (existingPackageNode) {
                            packageX = parseInt(existingPackageNode.x);
                            packageY = parseInt(existingPackageNode.y);
                            console.log(`使用已保存的规则包节点位置: ${packageName} (${packageX}, ${packageY})`);
                        } else {
                            packageX = parseInt(rules[0].x);
                            packageY = parseInt(rules[0].y);
                            console.log(`使用第一个规则的位置: ${packageName} (${packageX}, ${packageY})`);
                        }
                        
                        // 创建规则包节点
                        var packageNode = {
                            name: packageName,
                            type: 'RulesPackage',
                            x: packageX.toString(),
                            y: packageY.toString(),
                            width: rules[0].width,
                            height: rules[0].height,
                            rulesList: [],
                            connections: []
                        };
                        
                        // 将规则添加到规则包中，保持原始坐标
                        for (var j = 0; j < rules.length; j++) {
                            var rule = rules[j];
                            
                            packageNode.rulesList.push({
                                name: rule.name,
                                file: rule.file,
                                version: rule.version,
                                x: rule.x,
                                y: rule.y,
                                width: rule.width,
                                height: rule.height,
                                index: rule.index
                            });
                        }
                        
                        // 处理最后一个规则的连接关系
                        var lastRule = rules[rules.length - 1];
                        if (lastRule.connections && lastRule.connections.length > 0) {
                            for (var k = 0; k < lastRule.connections.length; k++) {
                                var conn = lastRule.connections[k];
                                var targetName = conn.toName;
                                
                                // 检查目标是否指向另一个合并的规则包
                                var targetPackageName = null;
                                for (var targetRule of json.nodes) {
                                    if (targetRule.name === targetName && targetRule.packageName) {
                                        targetPackageName = targetRule.packageName;
                                        break;
                                    }
                                }
                                
                                // 如果目标指向合并的规则包，则修改连接目标为规则包名称
                                if (targetPackageName && targetPackageName !== packageName) {
                                    conn.toName = targetPackageName;
                                }
                                
                                packageNode.connections.push(conn);
                            }
                        }
                        
                        mergedPackages.push(packageNode);
                        packageIndex++;
                    }
                }
                
                // 重建 nodes 数组：保留非规则节点 + 独立规则节点 + 合并后的规则包节点
                var newNodes = [];
                
                // 添加非规则节点
                for (var i = 0; i < json.nodes.length; i++) {
                    var node = json.nodes[i];
                    if (node.type !== 'Rule' && node.type !== '规则') {
                        newNodes.push(node);
                    }
                }
                
                // 添加独立规则节点
                newNodes = newNodes.concat(standaloneRules);
                
                // 添加合并后的规则包节点
                newNodes = newNodes.concat(mergedPackages);
                
                // 处理其他节点连接到规则包节点的连接关系
                for (var i = 0; i < newNodes.length; i++) {
                    var node = newNodes[i];
                    if (node.connections && node.connections.length > 0) {
                        for (var j = 0; j < node.connections.length; j++) {
                            var conn = node.connections[j];
                            var targetName = conn.toName;
                            
                            // 检查目标是否指向规则包中的某个规则
                            for (var packageName in packageGroups) {
                                var rules = packageGroups[packageName];
                                for (var k = 0; k < rules.length; k++) {
                                    var rule = rules[k];
                                    if (rule.name === targetName) {
                                        // 如果目标指向规则包中的规则，则修改连接目标为规则包名称
                                        conn.toName = packageName;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 更新 json.nodes
                json.nodes = newNodes;
            }
        }
        
        this.flowId = json.id;
        this.debug = json.debug;
        const libs = json.libraries || [];
        for (let lib of libs) {
            switch (lib.type) {
                case "Variable":
                    this.importVariableLibraries.push(lib.path);
                    break;
                case "Constant":
                    this.importConstantLibraries.push(lib.path);
                    break;
                case "Action":
                    this.importActionLibraries.push(lib.path);
                    break;
                case "Parameter":
                    this.importParameterLibraries.push(lib.path);
                    break;
            }
        }
        if (this.importVariableLibraries.length > 0) {
            this._refreshLibraries(this.importVariableLibraries, "vl.xml");
        }
        if (this.importConstantLibraries.length > 0) {
            this._refreshLibraries(this.importConstantLibraries, "cl.xml");
        }
        if (this.importActionLibraries.length > 0) {
            this._refreshLibraries(this.importActionLibraries, "al.xml");
        }
        if (this.importParameterLibraries.length > 0) {
            this._refreshLibraries(this.importParameterLibraries, "pl.xml");
        }
        for (let nodeJson of json.nodes) {
            nodeJson.w = nodeJson.width;
            nodeJson.h = nodeJson.height;
            switch (nodeJson.type) {
                case "Action":
                    nodeJson.type = '动作';
                    break;
                case "Script":
                    nodeJson.type = '脚本';
                    break;
                case "Decision":
                    nodeJson.type = '决策';
                    break;
                case "End":
                    nodeJson.type = '结束';
                    break;
                case "Start":
                    nodeJson.type = '开始';
                    break;
                case "Rule":
                    nodeJson.type = '规则';
                    break;
                case "RulePackage":
                    nodeJson.type = '知识包';
                    break;
                case "Fork":
                    nodeJson.type = '分支';
                    break;
                case "Join":
                    nodeJson.type = '聚合';
                    break;
                case "RulesPackage":
                    nodeJson.type = '规则包';
                    break;
            }
            const conns = nodeJson.connections || [];
            for (let conn of conns) {
                conn.to = conn.toName;
            }
            this.addNode(nodeJson);
        }
        for (let figure of this.context.allFigures) {
            if (!(figure instanceof Node)) {
                continue;
            }
            figure._buildConnections();
        }
    }

    getPropertiesProducer() {
        const _this = this;
        return function () {
            const g = $('<div></div>');
            const flowIdGroup = $(`<div class="form-group"><label>决策流ID</label></div>`);
            const flowIdText = $(`<input type="text" class="form-control">`);
            flowIdGroup.append(flowIdText);
            const _this = this;
            flowIdText.change(function () {
                _this.flowId = $(this).val();
            });
            flowIdText.val(this.flowId);
            g.append(flowIdGroup);

            const debugGroup = $(`<div class="form-group"><label>允许调试信息输出</label></div>`);
            const debugSelect = $(`<select class="form-control">
                <option value="true">是</option>
                <option value="false">否</option>
            </select>`);
            if (_this.debug) {
                debugSelect.val('true');
            } else {
                debugSelect.val('false');
            }
            debugGroup.append(debugSelect);
            debugSelect.change(function () {
                _this.debug = $(this).val() === 'true';
            });
            g.append(debugGroup);

            const libGroup = $('<div class="form-group"><label>库文件</label></div>');
            const addButton = $(`<span style="float: right;"><button type="button" class="btn btn-info"><i class="glyphicon glyphicon-plus"/> 添加</button></span>`);
            libGroup.append(addButton);
            addButton.click(function () {
                event.eventEmitter.emit(event.OPEN_KNOWLEDGE_TREE_DIALOG, {
                    project: window._project,
                    forLib: true,
                    callback: function (file, version) {
                        let fullFileName = file + (version === 'LATEST' ? '' : ':' + version);
                        const extPos = file.indexOf('.') + 1;
                        const extName = file.substring(extPos, file.length);
                        let importLibs = null;
                        if (extName === 'vl.xml') {
                            importLibs = _this.importVariableLibraries;
                        } else if (extName === 'cl.xml') {
                            importLibs = _this.importConstantLibraries;
                        } else if (extName === 'pl.xml') {
                            importLibs = _this.importParameterLibraries;
                        } else if (extName === 'al.xml') {
                            importLibs = _this.importActionLibraries;
                        } else {
                            alert(`Unknow lib [${file}]`);
                            return;
                        }
                        fullFileName = "jcr:" + fullFileName;
                        const pos = importLibs.indexOf(fullFileName);
                        if (pos > -1) {
                            MsgBox.alert(`库文件${file}已存在!`);
                            return;
                        }
                        const extCName = action.buildType(extName);
                        const newRow = $(`<tr>
                            <td style="font-size: 11px;word-break: break-all">${fullFileName}</td>
                            <td style="text-align: center">${extCName}</td>
                        </tr>`);
                        importLibs.push(fullFileName);
                        const delCol = $('<td style="text-align: center"/>');
                        newRow.append(delCol);
                        const delButton = $(`<div class="btn btn-link" style="padding: 0">删除</div>`);
                        delCol.append(delButton);
                        delButton.click(function () {
                            const pos = importLibs.indexOf(fullFileName);
                            importLibs.splice(pos, 1);
                            newRow.remove();
                            _this._refreshLibraries(importLibs, extName);
                        });
                        tbody.append(newRow);
                        _this._refreshLibraries(importLibs, extName);
                    }
                });
            });
            const table = $('<table class="table table-bordered" style="table-layout: fixed">');
            const thead = $(`<thead>
                <tr>
                    <td>库文件路径</td><td style="width: 60px">类型</td><td style="width: 50px">删除</td>
                </tr>
            </thead>`);
            table.append(thead);
            const tbody = $('<tbody>');
            table.append(tbody);

            function initLibraries(libraries) {
                for (let lib of libraries) {
                    let pos = lib.indexOf(":"), extName;
                    if (pos > -1) {
                        const libName = lib.substring(pos + 1, lib.length);
                        pos = libName.indexOf('.') + 1;
                        extName = libName.substring(pos, libName.length);
                    } else {
                        pos = lib.indexOf('.') + 1;
                        extName = lib.substring(pos, lib.length);
                    }
                    const extCName = action.buildType(extName);
                    const newRow = $(`<tr>
                    <td style="font-size: 11px;word-break: break-all">${lib}</td>
                    <td style="text-align: center">${extCName}</td>
                </tr>`);
                    const delCol = $('<td style="text-align: center"></td>');
                    newRow.append(delCol);
                    const delButton = $(`<div class="btn btn-link" style="padding: 0">删除</div>`);
                    delCol.append(delButton);
                    delButton.click(function () {
                        const pos = libraries.indexOf(lib);
                        libraries.splice(pos, 1);
                        newRow.remove();
                        _this._refreshLibraries(libraries, extName);
                    });
                    tbody.append(newRow);
                }
            }

            initLibraries(_this.importVariableLibraries);
            initLibraries(_this.importParameterLibraries);
            initLibraries(_this.importConstantLibraries);
            initLibraries(_this.importActionLibraries);

            libGroup.append(table);
            g.append(libGroup);
            return g;
        }
    }

    _refreshLibraries(importLibs, extName) {
        if (importLibs.length === 0) {
            return;
        }
        let libs = null;
        const _this = this;
        this._loadLibraries(importLibs, function (result) {
            if (extName === 'vl.xml') {
                libs = _this.variableLibraries;
                libs.splice(0, libs.length);
                for (let category of result) {
                    libs.push(...category);
                }
            } else if (extName === 'cl.xml') {
                libs = _this.constantLibraries;
                libs.splice(0, libs.length);
                for (let category of result) {
                    libs.push(...category.categories);
                }
            } else if (extName === 'pl.xml') {
                libs = _this.variableLibraries;
                let paramCategory, param1Category;
                for (let category of libs) {
                    if (category.name === '参数') {
                        paramCategory = category;
                    }
                    if (category.name === 'parameter') {
                        param1Category = category;
                    }
                }
                if (!paramCategory) {
                    paramCategory = {
                        type: 'variable',
                        name: '参数'
                    };
                    libs.push(paramCategory);
                }
                if (!param1Category) {
                    param1Category = {
                        type: 'variable',
                        name: 'parameter'
                    };
                    libs.push(param1Category);
                }
                paramCategory.variables = result[0];
                param1Category.variables = result[0];
            } else if (extName === 'al.xml') {
                importLibs = _this.importActionLibraries;
                libs = _this.actionLibraries;
                libs.splice(0, libs.length);
                libs.push(...result);
            }
            Event.eventEmitter.emit(CONSTANTS.LIB_CHANGE, {
                actionLibraries: _this.actionLibraries,
                constantCategories: _this.constantLibraries,
                parameterLibraries: _this.parameterLibraries,
                variableCategories: _this.variableLibraries
            });
        });
    };

    _loadLibraries(importLibs, callback) {
        let files = "";
        for (var i = 0; i < importLibs.length; i++) {
            const libFile = importLibs[i];
            if (i == 0) {
                files = libFile;
            } else {
                files += ";" + libFile;
            }
        }
        if (files.length < 2) {
            return;
        }
        var url = window._server + "/common/loadXml";
        $.ajax({
            url,
            data: {files},
            type: 'POST',
            error: function (response) {
                if (response && response.responseText) {
                    bootbox.alert("<span style='color: red'>加载库文件失败，服务端错误：" + response.responseText + "</span>");
                } else {
                    bootbox.alert("<span style='color: red'>加载库文件失败,服务端出错</span>");
                }
            },
            success: function (data) {
                callback(data);
            }
        });
    };
}