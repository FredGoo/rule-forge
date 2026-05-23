import forkSVG from './svg/fork.svg';
import BaseNode from './BaseNode.js';

export default class ForkNode extends BaseNode{
    getSvgIcon(){
        return forkSVG;
    }
    toXML(){
        const json=this.toJSON();
        json.type="ForkNode";
        const nodeName=this.getNodeName(json.type);
        const nodeProps=this.getXMLNodeBaseProps(json);
        let xml=`<${nodeName} ${nodeProps}>`;
        xml+=this.getFromConnectionsXML();
        xml+=`</${nodeName}>`;
        return xml;
    }
}