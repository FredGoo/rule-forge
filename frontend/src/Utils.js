window.iframe_id_ = 1;

export function nextIFrameId() {
    window.iframe_id_++;
    return '_iframe' + window.iframe_id_;
}

export function getParameter(name) {
    var reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)");
    var r = window.location.search.substr(1).match(reg);
    if (r != null) return r[2];
    return null;
}

export function buildProjectNameFromFile(file) {
    if (file.startsWith('/')) {
        file = file.substring(1);
        const pos = file.indexOf("/");
        return file.substring(0, pos);
    }
}

export function ajaxSave(url, parameters, callback) {
    $.ajax({
        type: 'POST',
        url,
        data: parameters,
        success: function (result) {
            if(result.status) {
                callback(result);
            } else {
                bootbox.alert(result.message || '保存失败');
            }
        },
        error: function (response) {
            if (response && response.status === 401) {
                bootbox.alert("权限不足，不能进行此操作.");
            } else {
                if (response && response.responseText) {
                    bootbox.alert("<span style='color: red'>服务端错误：" + response.responseText + "</span>");
                } else {
                    bootbox.alert("<span style='color: red'>服务端出错</span>");
                }
            }
        }
    });
}

export function formatDate(date, format) {
    if (typeof date === 'number') {
        date = new Date(date);
    }
    if (typeof date === 'string') {
        return date;
    }
    var o = {
        "M+": date.getMonth() + 1,
        "d+": date.getDate(),
        "H+": date.getHours(),
        "m+": date.getMinutes(),
        "s+": date.getSeconds()
    };
    if (/(y+)/.test(format))
        format = format.replace(RegExp.$1, (date.getFullYear() + "").substr(4 - RegExp.$1.length));
    for (var k in o)
        if (new RegExp("(" + k + ")").test(format))
            format = format.replace(RegExp.$1, (RegExp.$1.length === 1) ? (o[k]) : (("00" + o[k]).substr(("" + o[k]).length)));
    return format;
}

export function saveNewVersion(url, postData, cb) {
    $.ajax({
        type: 'POST',
        url: window._server + '/common/checkFileDirty',
        data: {
            filePath: postData.file,
            content: postData.content
        },
        success: function (res) {
            if(res.status) {
                if(res.data) {
                    // 处理可能的双重编码问题
                    let decodedFileName = decodeURIComponent(postData.file);
                    // 如果仍有乱码尝试第二次解码
                    if (decodedFileName.includes('%')) {
                        decodedFileName = decodeURIComponent(decodedFileName);
                    }
                    bootbox.confirm(`是否对【${decodedFileName}】生成新版本？`, function (result) {
                        if(result) {
                            ajaxSave(url, postData, function () {
                                cb();
                            })
                        }
                    })
                } else {
                    bootbox.alert("与最新版本无差异，无需生成新版本.");
                }
            } else {
                bootbox.alert("<span style='color: red'>服务端出错</span>");
            }
        },
        error: function (response) {
            if (response && response.status === 401) {
                bootbox.alert("权限不足，不能进行此操作.");
            } else {
                if (response && response.responseText) {
                    bootbox.alert("<span style='color: red'>服务端错误：" + response.responseText + "</span>");
                } else {
                    bootbox.alert("<span style='color: red'>服务端出错</span>");
                }
            }
        }
    });
}