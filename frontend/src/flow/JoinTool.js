import BaseTool from './BaseTool.js';
import JoinNode from './JoinNode.js';

export default class JoinTool extends BaseTool{
    getType(){
        return '聚合';
    }
    getIcon(){
        return `<i class="rf rf-join" style="color:#737383"></i>`
    }
    newNode(){
        return new JoinNode();
    }
    getConfigs(){
        return {
            out:1
        };
    }
    getPropertiesProducer(){
        const _this=this;
        return function (){
            const g=document.createElement('div');
            g.appendChild(_this.getCommonProperties(this));
            return g;
        }
    }
}
