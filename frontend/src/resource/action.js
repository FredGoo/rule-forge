import {ajaxSave} from '../Utils.js';
import * as componentEvent from '../components/componentEvent.js';

export const LOAD_MASTER_COMPLETED = 'load_master_completed';
export const LOAD_SLAVE_COMPLETE = 'load_slave_completed';
export const GENERATED_FIELDS = 'generated_fields';
export const IMPORT_FIELDS = 'IMPORT_FIELDS';
export const SAVE_COMPLETED = 'save_completed';
export const SAVE = 'save';

export function loadMasterData(files) {
    return function (dispatch) {
        var url = window._server + "/xml";
        $.ajax({
            url,
            type: 'POST',
            data: {files},
            success: function (data) {
                dispatch({type: LOAD_MASTER_COMPLETED, masterData: data[0]});
                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            },
            error: function (response) {
                if (response && response.responseText) {
                    bootbox.alert("<span style='color: red'>加载数据失败,服务端错误：" + response.responseText + "</span>");
                } else {
                    bootbox.alert("<span style='color: red'>加载数据失败,服务端出错</span>");
                }
                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            }
        });
    }
}

export function loadSlaveData(masterData) {
    return {type: LOAD_SLAVE_COMPLETE, masterRowData: masterData};
}

export function reFresh(file) {
    return dispatch => {
        dispatch(generateVariableLibrary(file));
    };
}

export function addVariable(data, file) {
    console.log(data)
    return function (dispatch) {
        var url = window._server + "/common/addVariable";
        $.ajax({
            url,
            type: 'POST',
            data,
            success: function (data) {
                dispatch(generateVariableLibrary(file));
                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            },
            error: function (response) {
                if (response && response.responseText) {
                    bootbox.alert("<span style='color: red'>保存数据失败,服务端错误：" + response.responseText + "</span>");
                } else {
                    bootbox.alert("<span style='color: red'>保存数据失败,服务端出错</span>");
                }
                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            }
        });
    }
}

export function generateVariableLibrary(file) {
    return function (dispatch) {
        let url = window._server + '/variableeditor/generateVariableLibrary';
        $.ajax({
            url,
            type: 'POST',
            success: function (data) {
                dispatch({type: LOAD_MASTER_COMPLETED, masterData: data.variableCategories});
                if (file) {
                    dispatch({type: SAVE, file, newVersion: false});
                }
                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            },
            error: function (response) {
                if (response && response.responseText) {
                    bootbox.alert("<span style='color: red'>生成字段失败,服务端错误：" + response.responseText + "</span>");
                } else {
                    bootbox.alert("<span style='color: red'>生成字段失败,服务端出错</span>");
                }
                componentEvent.eventEmitter.emit(componentEvent.HIDE_LOADING);
            }
        })
    }
}
export function save(newVersion, file) {
    return {newVersion, file, type: SAVE};
}
export function saveData(data, newVersion, file) {
    let xml = '<?xml version="1.0" encoding="utf-8"?>';
    xml += '<variable-library>';
    let errorInfo = '';
    const escapeXml = (unsafe) => {
        if (!unsafe) return '';
        return unsafe.replace(/[<>&'"]/g, (c) => {
            switch (c) {
                case '<': return '&lt;';
                case '>': return '&gt;';
                case '&': return '&amp;';
                case '\'': return '&apos;';
                case '"': return '&quot;';
                default: return c;
            }
        });
    };
    data.forEach((item, index) => {
        // if (!item.name || item.name.length < 1) {
        //     errorInfo = '变量分类名称不能为空.';
        //     return false;
        // }
        // if (!item.clazz || item.clazz.length < 1) {
        //     errorInfo = '变量类路径不能为空.';
        //     return false;
        // }
        xml += "<category name='" + escapeXml(item.name) + "' type='" + escapeXml(item.type) + "' clazz='" + escapeXml(item.clazz) + "'>";
        // xml += "<category name='" + item.name + "' type='" + item.type + "' clazz='" + item.clazz + "'>";
        const variables = item.variables;
        // if (!variables || variables.length === 0) {
        //     errorInfo = "变量分类[" + item.name + "]下未定义具体变量信息.";
        //     return false;
        // }
        let nameList = []
        let labelList = []
        variables.forEach((variable, i) => {
            // if (!variable.name || variable.name.length < 1) {
            //     errorInfo = '变量名不能为空';
            //     return false;
            // }
            // if (!variable.label || variable.label.length < 1) {
            //     errorInfo = '变量标题不能为空';
            //     return false;
            // }
            // if (!variable.type || variable.type.length < 1) {
            //     errorInfo = '变量数据类型不能为空';
            //     return false;
            // }
            // if (nameList.indexOf(variable.name) > -1) {
            //     errorInfo = '[' + item.name + ']变量名[' + variable.name + ']重复';
            //     return false;
            // }
            // if (labelList.indexOf(variable.label) > -1) {
            //     errorInfo = '[' + item.name + ']变量标题[' + variable.label + ']重复';
            //     return false;
            // }
            nameList.push(variable.name)
            labelList.push(variable.label)
            xml += "<var act='InOut' name='" + escapeXml(variable.name) + "' label='" + escapeXml(variable.label) + "' type='" + escapeXml(variable.type) + "'/>";
            // xml += "<var act='InOut' name='" + variable.name + "' label='" + variable.label + "' type='" + variable.type + "'/>";
        });
        if (errorInfo.length > 1) {
            return false;
        }
        xml += '</category>';
    });
    if (errorInfo.length > 1) {
        bootbox.alert(errorInfo + ',不能保存！');
        return;
    }
    xml += '</variable-library>';
    xml = encodeURIComponent(xml);
    let postData = {content: xml, file, newVersion};
    const url = window._server + '/common/saveFile';
    if (newVersion) {
        bootbox.prompt("请输入新版本描述.", function (versionComment) {
            if (!versionComment) {
                return;
            }
            postData.versionComment = versionComment;
            ajaxSave(url, postData, function () {
                // bootbox.alert('保存成功!');
            })
        });
    } else {
        ajaxSave(url, postData, function () {
            // bootbox.alert('保存成功!');
        })
    }
    return {type: SAVE_COMPLETED};
}