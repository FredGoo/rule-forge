import BaseNode from './BaseNode.js';
import rulesPackageSVG from './svg/rulesPackage.svg';

export default class RulesPackageNode extends BaseNode {
    constructor() {
        super();
        this.data = {
            type: '规则包',
            rulesList: [{
                name: '',        // 规则名称
                file: '',        // 目标规则文件
                version: '',     // 文件版本
            }]
        };
    }
    getSvgIcon(){
        return rulesPackageSVG;
      }
      // 新增：自动同步连线到最后一个 rule
      syncConnectionToLastRule() {
        if (!this.fromConnections || this.fromConnections.length === 0) return;
        const lastRule = this.data.rulesList && this.data.rulesList[this.data.rulesList.length - 1];
        if (!lastRule) return;
        for (let i = 0; i < this.fromConnections.length; i++) {
            const conn = this.fromConnections[i];
            if (conn && conn.to && conn.to !== this && conn.to.name) {
                // 如果目标节点是规则包，自动映射到其第一个 rule
                let targetName = conn.to.name;
                if (this._rulesPackageEntryMap && this._rulesPackageEntryMap[targetName]) {
                    targetName = this._rulesPackageEntryMap[targetName];
                }
                lastRule.to = targetName;
                return;
            }
        }
        lastRule.to = undefined;
    }

    // 新增：重新建立规则包内部规则之间的连接关系
    rebuildInternalConnections() {
        const rules = this.data.rulesList || [];
        if (rules.length <= 1) return;
        
        // 清除所有规则的内部连接
        for (let i = 0; i < rules.length; i++) {
            const rule = rules[i];
            // 如果规则有外部连接（指向规则包外），保留它
            if (rule.to && !this.isInternalRule(rule.to)) {
                continue;
            }
            // 清除内部连接
            rule.to = undefined;
        }
        
        // 重新建立内部连接：每个规则连接到下一个规则
        for (let i = 0; i < rules.length - 1; i++) {
            const currentRule = rules[i];
            const nextRule = rules[i + 1];
            currentRule.to = nextRule.name;
        }
        
        // 最后一个规则保持其外部连接（如果有的话）
        const lastRule = rules[rules.length - 1];
        if (!lastRule.to) {
            // 如果没有外部连接，清除内部连接
            lastRule.to = undefined;
        }
    }
    
    // 辅助方法：检查目标是否为规则包内部规则
    isInternalRule(targetName) {
        const rules = this.data.rulesList || [];
        return rules.some(rule => rule.name === targetName);
    }

    toXML() {
        // 重新建立内部连接关系
        this.rebuildInternalConnections();
        // 自动同步外部连接
        this.syncConnectionToLastRule();
        
        // 获取当前画布上的实际位置并同步到 this.x/this.y
        if (this.rect) {
            this.x = this.rect.attr("x");
            this.y = this.rect.attr("y");
        }
        
        let xml = '';
        const rules = this.data.rulesList || [];
        for (let i = 0; i < rules.length; i++) {
            const rule = rules[i];
            // 所有规则都使用规则包节点的位置
            const x = this.x || (100 + i * 40);
            const y = this.y || (100 + i * 40);
            xml += `<rule index="${i + 1}" name="${rule.name || ''}" file="${rule.file || ''}" version="${rule.version || ''}" x="${x}" y="${y}" packageName="${this.name || ''}" width="${this.width || 40}" height="${this.height || 70}">`;
            // 优先用 rule.to，否则连下一个 rule
            if (rule.to) {
                xml += `<connection g="" type="line" to="${rule.to}"/>`;
            } else if (i < rules.length - 1) {
                // 获取当前规则在 rulesList 中的下一个规则
                const nextRule = rules[i + 1];
                xml += `<connection g="" type="line" to="${nextRule.name || ''}"/>`;
            }
            xml += `</rule>`;
        }
        return xml;
    }

    initFromJson(json) {
        super.initFromJson(json);
        // 规则列表
        if (json.rulesList && Array.isArray(json.rulesList)) {
            this.data.rulesList = json.rulesList.map((rule, idx) => {
                // 补全 x/y/width/height/name/file/version
                return {
                    name: rule.name || '',
                    file: rule.file || '',
                    version: rule.version || '',
                    x: rule.x || this.x || (100 + idx * 40),
                    y: rule.y || this.y || (100 + idx * 40),
                    width: rule.width || this.width || 40,
                    height: rule.height || this.height || 70,
                    packageName: rule.packageName || '',
                };
            });
        } else {
            this.data.rulesList = [];
        }
    }

    validate() {
        let errorInfo = super.validate();
        if (errorInfo) return errorInfo;
        // 校验 rulesList
        if (!this.data.rulesList || !Array.isArray(this.data.rulesList) || this.data.rulesList.length === 0) {
            return `规则包节点【${this.name || ''}】的规则列表不能为空`;
        }
        for (let i = 0; i < this.data.rulesList.length; i++) {
            const rule = this.data.rulesList[i];
            if (!rule.name || rule.name.trim() === '') {
                return `规则包节点【${this.name || ''}】第${i + 1}条规则的名称不能为空`;
            }
            if (!rule.file || rule.file.trim() === '') {
                return `规则包节点【${this.name || ''}】第${i + 1}条规则的文件属性不能为空`;
            }
            if (!rule.version || rule.version.trim() === '') {
                return `规则包节点【${this.name || ''}】第${i + 1}条规则的版本属性不能为空`;
            }
        }
        return null;
    }

    // 重写 toJSON 方法，确保返回正确的节点位置信息
    toJSON() {
        const json = super.toJSON();
        if (this.rect) {
            json.x = this.rect.attr("x");
            json.y = this.rect.attr("y");
            json.w = this.rect.attr("width");
            json.h = this.rect.attr("height");
            // 关键：同步到 this.x/this.y，确保下次 addNode 用的是最新位置
            this.x = json.x;
            this.y = json.y;
        }
        return json;
    }
}