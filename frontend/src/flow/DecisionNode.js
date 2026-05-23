import decisionSVG from './svg/decision.svg';
import BaseNode from './BaseNode.js';

export default class DecisionNode extends BaseNode {
    getSvgIcon() {
        return decisionSVG;
    }

    toXML() {
        const json = this.toJSON();
        json.type = "DecisionNode";
        const nodeName = this.getNodeName(json.type);
        const nodeProps = this.getXMLNodeBaseProps(json);
        let xml = `<${nodeName} ${nodeProps} decision-type="${this.decisionType}">`;
        let itemXML = '';
        for (let item of this.decisionItems) {
            let s = null;
            if (this.decisionType === 'Percent') {
                s = `<item connection="${item.to}" percent="${item.percent}"></item>`;
            } else {
                if (item.script) {
                    s = `<item connection="${item.to}"><![CDATA[${item.script}]]></item>`;
                } else {
                    s = `<item connection="${item.to}"><![CDATA[]]></item>`;
                }
            }
            itemXML += s;
        }
        xml += itemXML;
        xml += this.getFromConnectionsXML();
        xml += `</${nodeName}>`;
        return xml;
    }

    initFromJson(json) {
        super.initFromJson(json);
        this.decisionType = json.decisionType;
        this.decisionItems = json.items;
    }

    validate() {
        let errorInfo = super.validate();
        if (errorInfo) return errorInfo;
        if (!this.decisionType || this.decisionType === '') {
            errorInfo = `节点${this.name}的决策类型属性不能为空`;
            return errorInfo;
        }
        if (this.decisionItems.length < 1) {
            errorInfo = `节点${this.name}的具体的决策项不能少于一个`;
        }
        let defaultItemTo = 0;
        if(this.decisionItems.length !== this.fromConnections.length) {
            errorInfo = `节点${this.name}的决策项未正确配置`;
            return errorInfo;
        }
        let totalPercent = 0; // 新增百分比总和统计
        for (let item of this.decisionItems) {
            if (this.decisionType === 'Percent') {
                if (!item.to || item.to === '' || !item.percent || item.percent === '' || !/^[1-9]\d*$|^0$/.test(item.percent)) {
                    errorInfo = `节点${this.name}的决策项未正确配置(输入整数且百分比之和等于100%)`;
                    break;
                }
                totalPercent += parseInt(item.percent, 10);
            } else {
                if (!item.to || item.to === '') {
                    errorInfo = `节点${this.name}的决策项未正确配置`;
                    break;
                }
                if (!item.script || item.script === '') {
                    if (defaultItemTo < 1) {
                        defaultItemTo++;
                    }
                    if (defaultItemTo > 1) {
                        errorInfo = `节点${this.name}的决策项未正确配置`;
                        break;
                    }
                }
            }
        }
        if (!errorInfo && this.decisionType === 'Percent' && totalPercent !== 100) {
            errorInfo = `节点${this.name}的决策项百分比之和不等于100%`;
        }
        return errorInfo;
    }
}