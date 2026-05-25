TreeNode=function(parentNode){
    this.parentNode=parentNode;
    this.container=document.createElement("table");
    this.container.className="nodeTable";
    this.container.setAttribute("border","0");
    this.container.setAttribute("cellpadding","0");
    this.container.setAttribute("cellspacing","0");
    var row=document.createElement("tr");
    this.col=document.createElement("td");
    this.col.setAttribute("align","center");
    row.appendChild(this.col);
    this.container.appendChild(row);
    this.nextColCount=0;
    this.lineCols=[];
    this.childrenNodes=[];
};

TreeNode.prototype.delete=function(){
    if(!this.parentNode){
        return;
    }
    _setDirty();
    var pos;
    var parentChildrenNodes=this.parentNode.childrenNodes;
    for(var i= 0;i<parentChildrenNodes.length;i++){
        if(parentChildrenNodes[i]===this){
            pos=i;
            break;
        }
    }
    parentChildrenNodes.splice(pos,1);
    this.parentNode.nextColCount-=2;
    this.parentNode.col.colSpan=this.parentNode.nextColCount;
    this.parentNode.lineCol.colSpan=this.parentNode.nextColCount;
    if(parentChildrenNodes.length>0){
        this.parentCol.remove();
        var parentLineCols=this.parentNode.lineCols;
        var pos;
        for(var j=0;j<this.newLineCols.length;j++){
            for(var i=0;i<parentLineCols.length;i++){
                if(parentLineCols[i]===this.newLineCols[j]){
                    pos=i;
                    break;
                }
            }
            parentLineCols.splice(pos,1);
        }
        if(parentLineCols.length>1){
            parentLineCols[0].style.borderTopStyle="none";
            parentLineCols[parentLineCols.length-1].style.borderTopStyle="none";
        }
        this.newLineCols[0].remove();
        this.newLineCols[1].remove();
    }else{
        this.parentNode.lineRow.remove();
        this.parentNode.nextRow.remove();
        this.parentNode.nextLineRow.remove();
        this.parentNode.nextRow=null;
        this.parentNode.lineCols.splice(0,this.parentNode.lineCols.length);
    }
};

TreeNode.prototype.addChild=function(type){
    _setDirty();
    if(!this.nextRow){
        this.nextRow=document.createElement("tr");
        this.nextRow.style.minHeight="40px";
        this.nextLineRow=document.createElement("tr");
        this.nextLineRow.style.height="20px";
        this.lineRow=document.createElement("tr");
        this.lineRow.style.height="20px";
        this.lineCol=document.createElement("td");
        var lineDiv=document.createElement("div");
        lineDiv.className="vertical-line";
        this.lineCol.appendChild(lineDiv);
        this.lineRow.appendChild(this.lineCol);
        this.container.appendChild(this.lineRow);
        this.container.appendChild(this.nextLineRow);
        this.container.appendChild(this.nextRow);
    }
    var newLineCols=this.newLine();

    var newCol=document.createElement("td");
    newCol.className="nodeContainer";
    newCol.setAttribute("align","center");
    newCol.colSpan=2;
    var childNode;
    if(type==="condition"){
        childNode=new ConditionTreeNode(this);
    }else if(type==="action"){
        childNode=new ActionTreeNode(this);
    }else if(type==="variable"){
        childNode=new VariableTreeNode(this,true);
    }
    childNode.newLineCols=newLineCols;
    childNode.parentCol=newCol;
    this.childrenNodes.push(childNode);
    newCol.appendChild(childNode.container);
    this.nextColCount+=2;
    this.nextRow.appendChild(newCol);
    if(this.nextColCount>1){
        this.col.colSpan=this.nextColCount;
        this.lineCol.colSpan=this.nextColCount;
    }
    return childNode;
};
TreeNode.prototype.initChildrenNodeData=function(data){
    var childrenNodes=[];
    var conditionTreeNodes=data["conditionTreeNodes"];
    if(conditionTreeNodes){
        childrenNodes=conditionTreeNodes;
    }
    var variableTreeNodes=data["variableTreeNodes"];
    if(variableTreeNodes){
        childrenNodes=childrenNodes.concat(variableTreeNodes);
    }
    var actionTreeNodes=data["actionTreeNodes"];
    if(actionTreeNodes){
        childrenNodes=childrenNodes.concat(actionTreeNodes);
    }
    if(!childrenNodes || childrenNodes.length===0){
        return;
    }
    for(var i=0;i<childrenNodes.length;i++){
        var childNodeData=childrenNodes[i];
        var newNode=this.addChild(childNodeData["nodeType"]);
        newNode.initData(childNodeData);
    }
};
TreeNode.prototype.newLine=function(){
    var lineCol1=document.createElement("td");
    lineCol1.style.borderRight="solid #4cae4c 2px";
    this.nextLineRow.appendChild(lineCol1);
    var lineCol2=document.createElement("td");
    lineCol2.style.borderLeft="solid #4cae4c 2px";
    this.nextLineRow.appendChild(lineCol2);
    if(this.lineCols.length>0){
        var lastLineCol=this.lineCols[this.lineCols.length-1];
        lastLineCol.style.borderTop="solid #4cae4c 2px";
        lineCol1.style.borderTop="solid #4cae4c 2px";
    }
    this.lineCols.push(lineCol1);
    this.lineCols.push(lineCol2);
    return [lineCol1,lineCol2];
};
