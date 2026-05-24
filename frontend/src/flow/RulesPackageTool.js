import BaseTool from './BaseTool.js';
import RulesPackageNode from './RulesPackageNode.js';
import rulesPackagePng from './svg/rulesPackage_icon.png';
import * as event from '../components/componentEvent.js';

export default class RulesPackageTool extends BaseTool {
    getType() {
        return '规则包';
    }
    getIcon() {
        return `<img src="${rulesPackagePng}" style="width: 16px; height: 16px;"/>`;
    }
    newNode() {
        return new RulesPackageNode();
    }
    getConfigs() {
        return { out: 1 };
    }
    getPropertiesProducer() {
        return function () {
            const self = this; // 节点对象
            const data = self.data || {};
            data.rulesList = data.rulesList || [];

            // 确保弹窗容器存在
            let dialogContainer = document.getElementById('__rules_info_dialog_container');
            if (!dialogContainer) {
                dialogContainer = document.createElement('div');
                dialogContainer.id = '__rules_info_dialog_container';
                document.body.appendChild(dialogContainer);
            }

            // 容器
            const containerEl = document.createElement('div');
            // 规则列表表格
            const table = document.createElement('table');
            table.className = 'table table-bordered';
            table.style.width = '100%';
            table.style.background = '#fff';
            table.style.marginBottom = '0';

            const thead = document.createElement('thead');
            const headTr = document.createElement('tr');
            const th1 = document.createElement('th');
            th1.style.textAlign = 'center';
            th1.style.width = '80px';
            th1.textContent = '规则顺序';
            headTr.appendChild(th1);
            const th2 = document.createElement('th');
            th2.style.textAlign = 'center';
            th2.textContent = '规则名称';
            headTr.appendChild(th2);
            const th3 = document.createElement('th');
            th3.style.textAlign = 'center';
            th3.style.width = '140px';
            th3.textContent = '操作';
            headTr.appendChild(th3);
            thead.appendChild(headTr);
            table.appendChild(thead);

            const tbody = document.createElement('tbody');
            table.appendChild(tbody);

            containerEl.appendChild(table);

            // 弹窗逻辑
            function openEditDialog(idx) {
                const rule = data.rulesList[idx];

                // 先移除已有弹窗
                const existingDialog = document.getElementById('__rules_info_dialog_jq');
                if (existingDialog) existingDialog.remove();
                // 显示简化的规则名称（去掉规则包前缀）
                let displayName = rule.name || '';
                if (self.name && displayName.startsWith(self.name + '.')) {
                    displayName = displayName.substring(self.name.length + 1);
                }
                // 弹窗结构
                const dialog = document.createElement('div');
                dialog.id = '__rules_info_dialog_jq';
                dialog.style.cssText = 'position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);z-index:999;width:400px;background:#fff;border:1px solid #ccc;border-radius:6px;box-shadow:0 2px 8px #0001;';

                // Header
                const header = document.createElement('div');
                header.style.cssText = 'border-bottom:1px solid #e5e5e5;padding:10px 16px;font-weight:bold;font-size:16px;display:flex;justify-content:space-between;align-items:center;cursor:move;user-select:none;';
                const headerSpan = document.createElement('span');
                headerSpan.textContent = '规则信息';
                header.appendChild(headerSpan);
                const closeSpan = document.createElement('span');
                closeSpan.className = 'close';
                closeSpan.style.cssText = 'cursor:pointer;font-size:20px;';
                closeSpan.textContent = '×';
                header.appendChild(closeSpan);
                dialog.appendChild(header);

                // Body
                const bodyDiv = document.createElement('div');
                bodyDiv.style.cssText = 'padding:16px 24px 0 24px;';

                // Name field
                const nameGroup = document.createElement('div');
                nameGroup.className = 'form-group';
                const nameLabel = document.createElement('label');
                nameLabel.textContent = '规则名称：';
                nameGroup.appendChild(nameLabel);
                const nameInput = document.createElement('input');
                nameInput.type = 'text';
                nameInput.className = 'form-control';
                nameInput.value = displayName;
                nameInput.style.width = '100%';
                nameGroup.appendChild(nameInput);
                bodyDiv.appendChild(nameGroup);

                // File field
                const fileGroup = document.createElement('div');
                fileGroup.className = 'form-group';
                const fileLabel = document.createElement('label');
                fileLabel.textContent = '目标规则文件';
                fileGroup.appendChild(fileLabel);
                const fileInputGroup = document.createElement('div');
                fileInputGroup.className = 'input-group';
                fileInputGroup.style.display = 'flex';
                const fileInput = document.createElement('input');
                fileInput.type = 'text';
                fileInput.className = 'form-control file';
                fileInput.value = rule.file || '';
                fileInput.style.flex = '1';
                fileInput.disabled = true;
                fileInputGroup.appendChild(fileInput);
                const selectFileBtn = document.createElement('button');
                selectFileBtn.className = 'btn btn-default select-file';
                selectFileBtn.title = '选择目标文件';
                selectFileBtn.style.marginLeft = '4px';
                selectFileBtn.innerHTML = '<i class="glyphicon glyphicon-search"></i>';
                fileInputGroup.appendChild(selectFileBtn);
                const openFileBtn = document.createElement('button');
                openFileBtn.className = 'btn btn-default open-file';
                openFileBtn.title = '打开这个文件';
                openFileBtn.style.marginLeft = '4px';
                openFileBtn.innerHTML = '<i class="glyphicon glyphicon-folder-open"></i>';
                fileInputGroup.appendChild(openFileBtn);
                fileGroup.appendChild(fileInputGroup);
                bodyDiv.appendChild(fileGroup);

                // Version field
                const versionGroup = document.createElement('div');
                versionGroup.className = 'form-group';
                const versionLabel = document.createElement('label');
                versionLabel.textContent = '文件版本';
                versionGroup.appendChild(versionLabel);
                const versionInput = document.createElement('input');
                versionInput.type = 'text';
                versionInput.className = 'form-control version';
                versionInput.value = rule.version || '';
                versionInput.disabled = true;
                versionGroup.appendChild(versionInput);
                bodyDiv.appendChild(versionGroup);

                // EventBean field
                const eventBeanGroup = document.createElement('div');
                eventBeanGroup.className = 'form-group';
                const eventBeanLabel = document.createElement('label');
                eventBeanLabel.textContent = '事件Bean';
                eventBeanGroup.appendChild(eventBeanLabel);
                const eventBeanInput = document.createElement('input');
                eventBeanInput.type = 'text';
                eventBeanInput.className = 'form-control eventBean';
                eventBeanInput.value = rule.eventBean || '';
                eventBeanGroup.appendChild(eventBeanInput);
                bodyDiv.appendChild(eventBeanGroup);

                dialog.appendChild(bodyDiv);

                // Footer
                const footer = document.createElement('div');
                footer.style.cssText = 'border-top:1px solid #e5e5e5;padding:12px 24px;text-align:right;background:#fafbfc;border-radius:0 0 6px 6px;';
                const saveBtn = document.createElement('button');
                saveBtn.className = 'btn btn-primary save';
                saveBtn.textContent = '保存';
                footer.appendChild(saveBtn);
                const cancelBtn = document.createElement('button');
                cancelBtn.className = 'btn btn-default cancel';
                cancelBtn.style.marginLeft = '8px';
                cancelBtn.textContent = '取消';
                footer.appendChild(cancelBtn);
                dialog.appendChild(footer);

                // 关闭弹窗
                closeSpan.addEventListener('click', function() {
                    dialog.remove();
                });
                cancelBtn.addEventListener('click', function() {
                    dialog.remove();
                });

                // 拖动弹窗
                let dragging = false, offsetX = 0, offsetY = 0;
                function onDialogMouseMove(e) {
                    if (dragging) {
                        dialog.style.left = (e.clientX - offsetX) + 'px';
                        dialog.style.top = (e.clientY - offsetY) + 'px';
                        dialog.style.transform = 'none';
                    }
                }
                function onDialogMouseUp() {
                    dragging = false;
                    document.removeEventListener('mousemove', onDialogMouseMove);
                    document.removeEventListener('mouseup', onDialogMouseUp);
                }
                header.addEventListener('mousedown', function(e) {
                    dragging = true;
                    const rect = dialog.getBoundingClientRect();
                    offsetX = e.clientX - rect.left;
                    offsetY = e.clientY - rect.top;
                    document.addEventListener('mousemove', onDialogMouseMove);
                    document.addEventListener('mouseup', onDialogMouseUp);
                });

                // 选择文件
                selectFileBtn.addEventListener('click', function() {
                    if (event.eventEmitter) {
                        event.eventEmitter.emit(event.OPEN_KNOWLEDGE_TREE_DIALOG, {
                            project: window._project,
                            callback: (file, version) => {
                                file = 'jcr:' + file;
                                fileInput.value = file;
                                versionInput.value = version;
                            }
                        });
                    } else {
                        alert('eventEmitter 未初始化，无法选择文件');
                    }
                });

                // 打开文件
                openFileBtn.addEventListener('click', function() {
                    const file = fileInput.value;
                    const version = versionInput.value;
                    if (!file) {
                        alert('请先选择文件');
                        return;
                    }
                    let fileName = file;
                    if (fileName.indexOf('jcr:') > -1) {
                        fileName = fileName.substring(4);
                    }
                    const pos = fileName.indexOf('.') + 1;
                    const extName = fileName.substring(pos);
                    let editorPath = './html';
                    if (extName === 'rs.xml') editorPath += '/ruleset-editor.html';
                    else if (extName === 'dt.xml') editorPath += '/decision-table-editor.html';
                    else if (extName === 'dtree.xml') editorPath += '/decision-tree-editor.html';
                    else if (extName === 'ul') editorPath += '/ul-editor.html';
                    else if (extName === 'sc') editorPath += '/score-card-editor.html';
                    else if (extName === 'rl.xml') editorPath += '/rule-flow-designer.html';
                    else if (extName === 'scc.xml') editorPath += '/complexscorecard-editor.html';
                    if (editorPath === window._server) {
                        alert('无法打开文件[' + file + ']');
                        return;
                    }
                    let fullPath = fileName;
                    if (version && version !== 'LATEST') {
                        fullPath += ':' + version;
                    }
                    editorPath += '?file=' + fullPath;
                    const componentEvent = window.parent && window.parent.componentEvent;
                    if (componentEvent) {
                        componentEvent.eventEmitter.emit('tree_node_click', {
                            fullPath,
                            name: fileName,
                            id: fullPath,
                            path: editorPath,
                            active: true
                        });
                    }
                });

                // 保存
                saveBtn.addEventListener('click', function() {
                    const name = nameInput.value.trim();
                    const file = fileInput.value;
                    const version = versionInput.value;
                    const eventBean = eventBeanInput.value;
                    if (!name) {
                        alert('规则名称不能为空');
                        return;
                    }
                    if (!file) {
                        alert('规则文件不能为空');
                        return;
                    }
                    if (!version) {
                        alert('规则版本不能为空');
                    }
                    // 确保规则名称包含规则包名称作为前缀，避免重名
                    let finalName = name;
                    if (self.name && !name.startsWith(self.name + '.')) {
                        finalName = self.name + '.' + name;
                    }
                    // 写回数据
                    rule.name = finalName;
                    rule.file = file;
                    rule.version = version;
                    rule.eventBean = eventBean;
                    dialog.remove();
                    renderRules();
                });

                // 显示弹窗
                document.body.appendChild(dialog);
            }

            // 渲染规则列表
            function renderRules() {
                tbody.innerHTML = '';
                data.rulesList.forEach((rule, idx) => {
                    // 显示简化的规则名称（去掉规则包前缀）
                    let displayName = rule.name || '';
                    if (self.name && displayName.startsWith(self.name + '.')) {
                        displayName = displayName.substring(self.name.length + 1);
                    }
                    const tr = document.createElement('tr');
                    const td1 = document.createElement('td');
                    td1.style.textAlign = 'center';
                    td1.textContent = idx + 1;
                    tr.appendChild(td1);
                    const td2 = document.createElement('td');
                    td2.style.textAlign = 'center';
                    td2.textContent = displayName;
                    tr.appendChild(td2);
                    const td3 = document.createElement('td');
                    td3.style.textAlign = 'center';

                    const editLink = document.createElement('a');
                    editLink.href = '#';
                    editLink.className = 'edit';
                    editLink.style.color = '#337ab7';
                    editLink.textContent = '编辑';
                    td3.appendChild(editLink);

                    const deleteLink = document.createElement('a');
                    deleteLink.href = '#';
                    deleteLink.className = 'delete';
                    deleteLink.style.cssText = 'color:red;margin-left:8px;';
                    deleteLink.textContent = '删除';
                    td3.appendChild(deleteLink);

                    const upLink = document.createElement('a');
                    upLink.href = '#';
                    upLink.className = 'up';
                    upLink.style.cssText = 'margin-left:8px;color:#4caf50;font-size:18px;';
                    upLink.textContent = '↑';
                    td3.appendChild(upLink);

                    const downLink = document.createElement('a');
                    downLink.href = '#';
                    downLink.className = 'down';
                    downLink.style.cssText = 'margin-left:8px;color:#4caf50;font-size:18px;';
                    downLink.textContent = '↓';
                    td3.appendChild(downLink);

                    tr.appendChild(td3);

                    // 编辑
                    editLink.addEventListener('click', function(e) {
                        e.preventDefault();
                        openEditDialog(idx);
                    });
                    // 删除
                    deleteLink.addEventListener('click', function(e) {
                        e.preventDefault();
                        data.rulesList.splice(idx, 1);
                        renderRules();
                    });
                    // 上移
                    upLink.addEventListener('click', function(e) {
                        e.preventDefault();
                        if (idx === 0) return;
                        [data.rulesList[idx - 1], data.rulesList[idx]] = [data.rulesList[idx], data.rulesList[idx - 1]];
                        renderRules();
                    });
                    // 下移
                    downLink.addEventListener('click', function(e) {
                        e.preventDefault();
                        if (idx === data.rulesList.length - 1) return;
                        [data.rulesList[idx], data.rulesList[idx + 1]] = [data.rulesList[idx + 1], data.rulesList[idx]];
                        renderRules();
                    });
                    tbody.appendChild(tr);
                });
            }
            renderRules();

            // 新增按钮
            const addBtnDiv = document.createElement('div');
            addBtnDiv.style.cssText = 'text-align:center;margin:10px 0;';
            const addBtn = document.createElement('button');
            addBtn.className = 'btn btn-primary';
            addBtn.style.minWidth = '80px';
            addBtn.textContent = '新增';
            addBtnDiv.appendChild(addBtn);
            addBtn.addEventListener('click', function() {
                data.rulesList.push({ name: '', file: '', version: '', eventBean: '' });
                renderRules();
            });
            containerEl.appendChild(addBtnDiv);

            return containerEl;
        }
    }
}
