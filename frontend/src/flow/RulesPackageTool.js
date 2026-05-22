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
            let $dialogContainer = $('#__rules_info_dialog_container');
            if ($dialogContainer.length === 0) {
                $dialogContainer = $('<div id="__rules_info_dialog_container"></div>');
                $('body').append($dialogContainer);
            }

            // 容器
            const $container = $('<div></div>');
            // 规则列表表格
            const $table = $(
                `<table class="table table-bordered" style="width:100%;background:#fff;margin-bottom:0;">
                    <thead>
                        <tr>
                            <th style="text-align:center;width:80px;">规则顺序</th>
                            <th style="text-align:center;">规则名称</th>
                            <th style="text-align:center;width:140px;">操作</th>
                        </tr>
                    </thead>
                    <tbody></tbody>
                </table>`
            );
            $container.append($table);

            // 弹窗逻辑
            function openEditDialog(idx) {
                const rule = data.rulesList[idx];

                // 先移除已有弹窗
                $('#__rules_info_dialog_jq').remove();
                // 显示简化的规则名称（去掉规则包前缀）
                let displayName = rule.name || '';
                if (self.name && displayName.startsWith(self.name + '.')) {
                    displayName = displayName.substring(self.name.length + 1);
                }
                // 弹窗结构
                const $dialog = $(`
                  <div id="__rules_info_dialog_jq" style="position:fixed;left:50%;top:50%;transform:translate(-50%,-50%);z-index:999;width:400px;background:#fff;border:1px solid #ccc;border-radius:6px;box-shadow:0 2px 8px #0001;">
                    <div style="border-bottom:1px solid #e5e5e5;padding:10px 16px;font-weight:bold;font-size:16px;display:flex;justify-content:space-between;align-items:center;cursor:move;user-select:none;">
                      <span>规则信息</span>
                      <span class="close" style="cursor:pointer;font-size:20px;">×</span>
                    </div>
                    <div style="padding:16px 24px 0 24px;">
                      <div class="form-group">
                        <label>规则名称：</label>
                        <input type="text" class="form-control" value="${displayName}" style="width:100%;" />
                      </div>
                      <div class="form-group">
                        <label>目标规则文件</label>
                        <div class="input-group" style="display:flex;">
                          <input type="text" class="form-control file" value="${rule.file || ''}" style="flex:1;" disabled />
                          <button class="btn btn-default select-file" title="选择目标文件" style="margin-left:4px;"><i class="glyphicon glyphicon-search"></i></button>
                          <button class="btn btn-default open-file" title="打开这个文件" style="margin-left:4px;"><i class="glyphicon glyphicon-folder-open"></i></button>
                        </div>
                      </div>
                      <div class="form-group">
                        <label>文件版本</label>
                        <input type="text" class="form-control version" value="${rule.version || ''}" disabled />
                      </div>
                      <div class="form-group">
                        <label>事件Bean</label>
                        <input type="text" class="form-control eventBean" value="${rule.eventBean || ''}" />
                      </div>
                    </div>
                    <div style="border-top:1px solid #e5e5e5;padding:12px 24px;text-align:right;background:#fafbfc;border-radius:0 0 6px 6px;">
                      <button class="btn btn-primary save">保存</button>
                      <button class="btn btn-default cancel" style="margin-left:8px;">取消</button>
                    </div>
                  </div>
                `);

                // 关闭弹窗
                $dialog.find('.close, .cancel').on('click', function() {
                  $dialog.remove();
                });

                // 拖动弹窗
                let dragging = false, offsetX = 0, offsetY = 0;
                $dialog.find('div').first().on('mousedown', function(e) {
                  dragging = true;
                  const rect = $dialog[0].getBoundingClientRect();
                  offsetX = e.clientX - rect.left;
                  offsetY = e.clientY - rect.top;
                  $(document).on('mousemove.dialog', function(e) {
                    if (dragging) {
                      $dialog.css({
                        left: e.clientX - offsetX,
                        top: e.clientY - offsetY,
                        transform: 'none'
                      });
                    }
                  }).on('mouseup.dialog', function() {
                    dragging = false;
                    $(document).off('.dialog');
                  });
                });

                // 选择文件
                $dialog.find('.select-file').on('click', function() {
                  const fileInput = $dialog.find('.file');
                  const versionInput = $dialog.find('.version');
                  if (event.eventEmitter) {
                    event.eventEmitter.emit(event.OPEN_KNOWLEDGE_TREE_DIALOG, {
                      project: window._project,
                      callback: (file, version) => {
                        file = 'jcr:' + file;
                        fileInput.val(file);
                        versionInput.val(version);
                      }
                    });
                  } else {
                    alert('eventEmitter 未初始化，无法选择文件');
                  }
                });

                // 打开文件
                $dialog.find('.open-file').on('click', function() {
                  const file = $dialog.find('.file').val();
                  const version = $dialog.find('.version').val();
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
                $dialog.find('.save').on('click', function() {
                  const name = $dialog.find('input[type="text"]').eq(0).val().trim();
                  const file = $dialog.find('.file').val();
                  const version = $dialog.find('.version').val();
                  const eventBean = $dialog.find('.eventBean').val();
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
                  $dialog.remove();
                  renderRules();
                });

                // 显示弹窗
                $('body').append($dialog);
            }

            // 渲染规则列表
            function renderRules() {
                const $tbody = $table.find('tbody');
                $tbody.empty();
                data.rulesList.forEach((rule, idx) => {
                    // 显示简化的规则名称（去掉规则包前缀）
                    let displayName = rule.name || '';
                    if (self.name && displayName.startsWith(self.name + '.')) {
                        displayName = displayName.substring(self.name.length + 1);
                    }
                    const $tr = $(
                        `<tr>
                            <td style="text-align:center;">${idx + 1}</td>
                            <td style="text-align:center;">${displayName}</td>
                            <td style="text-align:center;">
                                <a href="#" class="edit" style="color:#337ab7;">编辑</a>
                                <a href="#" class="delete" style="color:red;margin-left:8px;">删除</a>
                                <a href="#" class="up" style="margin-left:8px;color:#4caf50;font-size:18px;">↑</a>
                                <a href="#" class="down" style="margin-left:8px;color:#4caf50;font-size:18px;">↓</a>
                            </td>
                        </tr>`
                    );
                    // 编辑
                    $tr.find('.edit').on('click', function(e) {
                        e.preventDefault();
                        openEditDialog(idx);
                    });
                    // 删除
                    $tr.find('.delete').on('click', function(e) {
                        e.preventDefault();
                        data.rulesList.splice(idx, 1);
                        renderRules();
                    });
                    // 上移
                    $tr.find('.up').on('click', function(e) {
                        e.preventDefault();
                        if (idx === 0) return;
                        [data.rulesList[idx - 1], data.rulesList[idx]] = [data.rulesList[idx], data.rulesList[idx - 1]];
                        renderRules();
                    });
                    // 下移
                    $tr.find('.down').on('click', function(e) {
                        e.preventDefault();
                        if (idx === data.rulesList.length - 1) return;
                        [data.rulesList[idx], data.rulesList[idx + 1]] = [data.rulesList[idx + 1], data.rulesList[idx]];
                        renderRules();
                    });
                    $tbody.append($tr);
                });
            }
            renderRules();

            // 新增按钮
            const $addBtn = $(
                `<div style="text-align:center;margin:10px 0;">
                    <button class="btn btn-primary" style="min-width:80px;">新增</button>
                </div>`
            );
            $addBtn.find('button').on('click', function() {
                data.rulesList.push({ name: '', file: '', version: '', eventBean: '' });
                renderRules();
            });
            $container.append($addBtn);

            return $container;
        }
    }
}