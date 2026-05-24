VariableTreeNode=function(parentNode,allowDelete){
    this.allowDelete=allowDelete;
    TreeNode.call(this,parentNode);
    this.initNode();
};
VariableTreeNode.prototype=Object.create(TreeNode.prototype);
VariableTreeNode.prototype.constructor=VariableTreeNode;
VariableTreeNode.prototype.initNode=function(){
    var nodeContainer=document.createElement("div");
    nodeContainer.className="node varNode";
    this.col.appendChild(nodeContainer);
    var contentContainer=document.createElement("span");
    nodeContainer.appendChild(contentContainer);
    this.condition=new ruleforge.ConditionLeft(contentContainer);
    var self=this;
    var operations=document.createElement("span");
    operations.className="operations";
    operations.innerHTML="<i class='icon-ok-circle'></i>";
    nodeContainer.appendChild(operations);
    var menuItems=[];
    menuItems.push({
        name:"addCondition",
        label:"添加条件",
        onClick:function(){
            self.addChild("condition");
        }
    });
    if(this.allowDelete){
        menuItems.push({
            name:"delete",
            label:"删除",
            onClick:function(){
                RuleForge.confirm("真的要删除当前节点？",function(){
                    self.delete();
                });
            }
        });
    }
    var menu=new RuleForge.menu.Menu({menuItems:menuItems});
    operations.addEventListener('click',function(e){
        menu.show(e);
    });
};
VariableTreeNode.prototype.initData=function(data){
    if(!data){
        return;
    }
    var left=data["left"];
    this.condition.initData(left);
    TreeNode.prototype.initChildrenNodeData.call(this,data);
};
VariableTreeNode.prototype.toXml=function(){
    if(this.childrenNodes.length==0){
        throw "变量节点下至少要有一个条件节点.";
    }
    var xml="<variable-tree-node>";
    xml+=this.condition.toXml();
    this.childrenNodes.forEach(function(childNode) {
        xml+=childNode.toXml();
    });
    xml+="</variable-tree-node>";
    return xml;
};
