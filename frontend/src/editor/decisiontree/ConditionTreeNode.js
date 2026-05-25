ConditionTreeNode=function(parentNode){
    TreeNode.call(this,parentNode);
    this.initNode();
};
ConditionTreeNode.prototype=Object.create(TreeNode.prototype);
ConditionTreeNode.prototype.constructor=ConditionTreeNode;
ConditionTreeNode.prototype.initNode=function(){
    var nodeContainer=document.createElement("div");
    nodeContainer.className="node conditionNode";
    this.col.appendChild(nodeContainer);
    var self=this;
    var contentContainer=document.createElement("span");
    this.contentContainer=contentContainer;
    nodeContainer.appendChild(contentContainer);
    this.operator=new ruleforge.ComparisonOperator(function(){
        self.inputType=self.operator.getInputType();
        if(self.inputType){
            contentContainer.appendChild(self.inputType.getContainer());
        }
    });
    contentContainer.appendChild(this.operator.getContainer());

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
    menuItems.push({
        name:"addVariable",
        label:"添加变量",
        onClick:function(){
            self.addChild("variable");
        }
    });
    menuItems.push({
        name:"addAction",
        label:"添加动作",
        onClick:function(){
            self.addChild("action");
        }
    });
    menuItems.push({
        name:"delete",
        label:"删除",
        onClick:function(){
            RuleForge.confirm("真的要删除当前节点？",function(){
                self.delete();
            });
        }
    });
    var menu=new RuleForge.menu.Menu({menuItems:menuItems});
    operations.addEventListener('click',function(e){
        menu.show(e);
    });
};

ConditionTreeNode.prototype.initData=function(data){
    if(!data){
        return;
    }
    var op=data["op"];
    this.operator.setOperator(op);
    var value=data["value"];
    this.operator.initRightValue(value);
    this.inputType=this.operator.getInputType();
    if(this.inputType){
        this.contentContainer.appendChild(this.inputType.getContainer());
    }
    TreeNode.prototype.initChildrenNodeData.call(this,data);
};

ConditionTreeNode.prototype.toXml=function(){
    if(this.childrenNodes.length==0){
        throw "条件节点下至少要有一个动作节点.";
    }
    var xml="<condition-tree-node op=\""+this.operator.getOperator()+"\">";
    if(this.inputType){
        xml+=this.inputType.toXml();
    }
    this.childrenNodes.forEach(function(childNode) {
        xml+=childNode.toXml();
    });
    xml+="</condition-tree-node>";
    return xml;
};
