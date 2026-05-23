import BaseTool from './BaseTool.js';
import PackageNode from './PackageNode.js';

export default class PackageTool extends BaseTool {
    getType() {
        return 'rp';
    }

    getIcon() {
        return `<i class="rf rf-package" style="color:#737383"/>`
    }

    newNode() {
        return new PackageNode();
    }

    getConfigs() {
        return {
            out: 1
        };
    }

    getPropertiesProducer() {
        const _this = this;
        return function () {
            const g = $(`<div></div>`);
            const packageIdGroup = $(`<div class="form-group"><label>知识包ID</label></div>`);
            const packageIdSelect = $(`<select class="form-control"><option/></select>`);
            packageIdGroup.append(packageIdSelect);
            const self = this;
            packageIdSelect.change(function () {
                self.packageId = $(this).val();
            });
            if (!_this.packages) {
                fetch(window._server + '/ruleflowdesigner/loadPackages?project=' + window._project, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'}
                }).then(function(response) {
                    if (!response.ok) throw response;
                    return response.json();
                }).then(function (packages) {
                    _this.packages = packages;
                    for (let p of packages) {
                        packageIdSelect.append(`<option>${p.id}</option>`);
                    }
                    packageIdSelect.val(self.packageId);
                }).catch(function () {
                    alert('加载知识包出错！');
                });
            } else {
                for (let p of _this.packages) {
                    packageIdSelect.append(`<option>${p.id}</option>`);
                }
                packageIdSelect.val(this.packageId);
            }
            g.append(packageIdGroup);
            g.append(_this.getCommonProperties(this));
            return g;
        }
    }
}