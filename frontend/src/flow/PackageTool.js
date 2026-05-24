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
            const g = document.createElement('div');
            const packageIdGroup = document.createElement('div');
            packageIdGroup.className = 'form-group';
            const packageIdLabel = document.createElement('label');
            packageIdLabel.textContent = '知识包ID';
            packageIdGroup.appendChild(packageIdLabel);
            const packageIdSelect = document.createElement('select');
            packageIdSelect.className = 'form-control';
            const defaultOption = document.createElement('option');
            packageIdSelect.appendChild(defaultOption);
            packageIdGroup.appendChild(packageIdSelect);
            const self = this;
            packageIdSelect.addEventListener('change', function () {
                self.packageId = this.value;
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
                        const option = document.createElement('option');
                        option.textContent = p.id;
                        packageIdSelect.appendChild(option);
                    }
                    packageIdSelect.value = self.packageId || '';
                }).catch(function () {
                    alert('加载知识包出错！');
                });
            } else {
                for (let p of _this.packages) {
                    const option = document.createElement('option');
                    option.textContent = p.id;
                    packageIdSelect.appendChild(option);
                }
                packageIdSelect.value = this.packageId || '';
            }
            g.appendChild(packageIdGroup);
            g.appendChild(_this.getCommonProperties(this));
            return g;
        }
    }
}
