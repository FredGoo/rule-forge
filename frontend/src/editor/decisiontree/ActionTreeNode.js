ActionTreeNode=function(parentNode){
    TreeNode.call(this,parentNode);
    this.actionTypes=[];
    this.initNode();
};
ActionTreeNode.prototype=Object.create(TreeNode.prototype);
ActionTreeNode.prototype.constructor=ActionTreeNode;
ActionTreeNode.prototype.initNode=function(){
    this.nodeContainer=document.createElement("div");
    this.nodeContainer.className="node actionNode";
    this.col.appendChild(this.nodeContainer);
    this.actionsContainer=document.createElement("span");
    this.actionsContainer.style.display="inline-block";
    this.nodeContainer.appendChild(this.actionsContainer);
    this.addAction();
    var self=this;
    var operations=document.createElement("span");
    operations.className="operations";
    operations.innerHTML="<i class='icon-ok-circle'></i>";
    this.nodeContainer.appendChild(operations);
    var menuItems=[];
    menuItems.push({
        name:"delete",
        label:"删除",
        onClick:function(){
            RuleForge.confirm("真的要删除当前节点？",function(){
                self.delete();
            });
        }
    });
    menuItems.push({
        name:"addAction",
        label:"添加动作",
        onClick:function(){
            self.addAction(true);
        }
    });
    var menu=new RuleForge.menu.Menu({menuItems:menuItems});
    operations.addEventListener('click',function(e){
        menu.show(e);
    });
};
ActionTreeNode.prototype.addAction=function(notfirst){
    var actionContainer=document.createElement("span");
    if(notfirst){
        actionContainer.style.display="block";
    }
    var delIcon=document.createElement("i");
    delIcon.className="icon-minus-sign icon-large";
    delIcon.style.cssText="color: #ac2925;padding-right: 5px";
    actionContainer.appendChild(delIcon);
    this.actionsContainer.appendChild(actionContainer);
    var newActionType=new ruleforge.ActionType(actionContainer);
    this.actionTypes.push(newActionType);
    actionContainer.actionType=newActionType;
    var self=this;
    delIcon.addEventListener('click',function(){
        if(self.actionTypes.length===1){
            RuleForge.alert("动作至少要有一个.");
            return;
        }
        var pos=-1;
        self.actionTypes.forEach(function(at) {
            if(at===actionContainer.actionType){
                pos=i;
                return false;
            }
        });
        if(pos!==-1){
            self.actionTypes.splice(pos,1);
            actionContainer.remove();
        }else{
            RuleForge.alert("未找到要删除的动作对象.");
        }
    });
    return newActionType;
};
ActionTreeNode.prototype.initData=function(data){
    if(!data){
        return;
    }
    var actions=data["actions"];
    if(!actions || actions.length===0){
        return;
    }
    this.actionTypes[0].parentContainer.remove();
    this.actionTypes.splice(0,1);
    for(var i=0;i<actions.length;i++){
        var action=actions[i];
        var newActionType=this.addAction(i!==0);
        newActionType.initData(action);
    }
};

ActionTreeNode.prototype.toXml=function(){
    var xml="<action-tree-node>";
    this.actionTypes.forEach(function(actionType) {
        xml+=actionType.toXml();
    });
    xml+="</action-tree-node>";
    return xml;
};
