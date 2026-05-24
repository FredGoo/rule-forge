import BaseTool from './BaseTool.js';
import ActionNode from './ActionNode.js';

export default class ActionTool extends BaseTool{
    getType(){
        return '动作';
    }
    getIcon(){
        return `<i class="rf rf-action" style="color:#737383"></i>`
    }
    newNode(){
        return new ActionNode();
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
            const actionBeanGroup=document.createElement('div');
            actionBeanGroup.className='form-group';
            const actionBeanLabel=document.createElement('label');
            actionBeanLabel.textContent='动作Bean';
            actionBeanGroup.appendChild(actionBeanLabel);
            const actionBeanText=document.createElement('input');
            actionBeanText.type='text';
            actionBeanText.className='form-control';
            actionBeanText.title='一个实现了com.ruleforge.model.flow.FlowAction接口并配置到Spring中的Bean的ID';
            actionBeanGroup.appendChild(actionBeanText);
            const self=this;
            actionBeanText.addEventListener('change',function(){
                self.actionBean=this.value;
            });
            actionBeanText.value=this.actionBean || '';
            g.appendChild(actionBeanGroup);
            g.appendChild(_this.getCommonProperties(this));
            return g;
        }
    }
}
