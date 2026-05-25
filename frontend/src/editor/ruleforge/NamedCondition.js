ruleforge.NamedCondition=function(context,parentContainer,parentJoin){
	this.context=context;
	this.parentJoin=parentJoin;
	this.variable=null;
	this.container=document.createElement("span");
	parentContainer.appendChild(this.container);
	this.arithmetic=new ruleforge.SimpleArithmetic();

	this.label=generateContainer();
	this.container.appendChild(this.label);
	this.label.style.color = "#673AB7";
	this.label.textContent = "请选择属性";
	this.valueContainer=document.createElement("span");
	this.container.appendChild(this.valueContainer);
	this.initMenu();

};
ruleforge.NamedCondition.prototype.initMenu=function(){
	var self=this,menuItems=[];
	var variables=[];
	if(this.parentJoin.variableCategory){
		variables=this.parentJoin.variableCategory.variables || [];
	}
	for(let variable of variables){
		menuItems.push({
			label:variable.label,
			variable:variable,
			onClick:function (item) {
				self.variableName=variable.name;
				self.variableLabel=variable.label;
				self.datatype=variable.type;
				self.label.textContent = item.label;
				if(self.operator){
					self.operator.getContainer().style.display='';
				}else{
					self.operator=new ruleforge.ComparisonOperator(function(){
						self.inputType=self.operator.getInputType();
						if(self.inputType){
							self.container.appendChild(self.inputType.getContainer());
						}
					});
					self.container.appendChild(self.operator.getContainer());
				}
				window._setDirty();
			}
		});
	}
	this.menu=new RuleForge.menu.Menu({menuItems});
	this.label.addEventListener('click',function(e){
		self.menu.show(e);
	});
};
ruleforge.NamedCondition.prototype.initData=function(data){
	this.variableName=data["variableName"];
	this.variableLabel=data["variableLabel"];
	this.datatype=data["datatype"];
	this.label.textContent = this.variableLabel;
	var self=this;
	if(this.operator){
		this.operator.getContainer().style.display='';
	}else{
		this.operator=new ruleforge.ComparisonOperator(function(){
			self.inputType=self.operator.getInputType();
			if(self.inputType){
				self.container.appendChild(self.inputType.getContainer());
			}
		});
		this.container.appendChild(this.operator.getContainer());
	}
	var op=data["op"];
	this.operator.setOperator(op);
	this.operator.initRightValue(data["value"]);
	this.inputType=this.operator.getInputType();
	if(this.inputType){
		this.container.appendChild(this.inputType.getContainer());
	}
};
ruleforge.NamedCondition.prototype.toXml=function(){
	if(!this.variableName){
		throw "请定义条件.";
	}
	var xml="<named-criteria op=\""+this.operator.getOperator()+"\" var=\""+this.variableName+"\" var-label=\""+this.variableLabel+"\" datatype=\""+this.datatype+"\">";
	if(this.inputType){
		xml+=this.inputType.toXml();
	}
	xml+="</named-criteria>";
	return xml;
};
ruleforge.NamedCondition.prototype.getVariableValue=function(){
	return this.variableValue;
};
ruleforge.NamedCondition.prototype.getOperator=function(){
	return this.operator;
};
ruleforge.NamedCondition.prototype.getInputType=function(){
	return this.inputType;
};