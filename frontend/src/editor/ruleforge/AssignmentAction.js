ruleforge.AssignmentAction=function(rule){
	this.container=document.createElement("span");
	this.label=generateContainer();
	this.container.appendChild(this.label);
	RuleForge.setDomContent(this.label,"请选择值类型");
	this.label.css({
		"color":"blue"
	});
	this.valueContainer=document.createElement("span");
	this.container.appendChild(this.valueContainer);
	this.leftVariable=new ruleforge.VariableValue(null,null,"Out");
	this.leftParameter=new ruleforge.ParameterValue(null,null,"Out");
	this.leftReference=new ruleforge.NamedReferenceValue(null,null,rule);
	this.valueContainer.appendChild(this.leftVariable.getContainer());
	this.valueContainer.appendChild(this.leftParameter.getContainer());
	this.valueContainer.appendChild(this.leftReference.getContainer());

	this.leftVariable.getContainer().style.display='none';
	this.leftParameter.getContainer().style.display='none';
	this.leftReference.getContainer().style.display='none';

	var equals=document.createElement("span");
	RuleForge.setDomContent(equals,"=");
	equals.css({
		color:"red",
		"fontWeight":"blod"
	});
	this.container.appendChild(equals);
	this.inputType=new ruleforge.InputType(null,null,null,rule);
	this.container.appendChild(this.inputType.getContainer());
	var self=this;
	self.menu=new RuleForge.menu.Menu({
		menuItems:[{
			label:"选择变量",
			onClick:function(){
				self.leftParameter.getContainer().style.display='none';
				self.leftReference.getContainer().style.display='none';
				self.leftVariable.getContainer().style.display='';
				self.type="variable";
				RuleForge.setDomContent(self.label,".");
				self.label.css({
					"color":"white"
				});
			}
		},{
			label:"选择参数",
			onClick:function(){
				self.leftVariable.getContainer().style.display='none';
				self.leftReference.getContainer().style.display='none';
				self.leftParameter.getContainer().style.display='';
				self.type="parameter";
				RuleForge.setDomContent(self.label,".");
				self.label.css({
					"color":"white"
				});
			}
		}/*,{
			label:"选择命名变量",
			onClick:function(){
				self.leftVariable.getContainer().style.display='none';
				self.leftParameter.getContainer().style.display='none';
				self.leftReference.getContainer().style.display='';
				self.type="NamedReference";
				RuleForge.setDomContent(self.label,".");
				self.label.css({
					"color":"white"
				});
			}
		}*/]
	});
	this.label.addEventListener('click',function(e){
		self.menu.show(e);
	});
};
ruleforge.AssignmentAction.prototype.initData=function(data){
	if(!data){
		return;
	}
	this.type=data["type"];
	if(this.type && this.type=="parameter"){
		this.leftParameter.setValue(data);
		this.leftVariable.getContainer().style.display='none';
		this.leftReference.getContainer().style.display='none';
		this.leftParameter.getContainer().style.display='';
	}else if(this.type && this.type=="NamedReference"){
        this.leftReference.initData(data);
        this.leftVariable.getContainer().style.display='none';
        this.leftParameter.getContainer().style.display='none';
        this.leftReference.getContainer().style.display='';
    }else{
		this.type="variable";
		this.leftVariable.setValue(data);
		this.leftParameter.getContainer().style.display='none';
        this.leftReference.getContainer().style.display='none';
        this.leftVariable.getContainer().style.display='';
    }
	var value=data["value"];
	if(value){
		var valueType=value["valueType"];
		this.inputType.setValueType(valueType, value);
	}
	RuleForge.setDomContent(this.label,".");
	this.label.css({
		"color":"white"
	});
};
ruleforge.AssignmentAction.prototype.toXml=function(){
	var xml="<var-assign ";
	if(this.type=="variable"){
		xml+=this.leftVariable.toXml();
	}else if(this.type=="NamedReference"){
        xml+=this.leftReference.toXml();
    }else{
		xml+=this.leftParameter.toXml();
	}
	xml+=" type=\""+this.type+"\">";
	xml+=this.inputType.toXml();
	xml+="</var-assign>";
	return xml;
};
ruleforge.AssignmentAction.prototype.getContainer=function(){
	return this.container;
};