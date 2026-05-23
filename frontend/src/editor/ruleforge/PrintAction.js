ruleforge.PrintAction=function(rule){
	this.container=$("<span>");
	this.beforeContainer=$("<span>");
	RuleForge.setDomContent(this.beforeContainer,"");
	this.container.append(this.beforeContainer);
	this.inputType=new ruleforge.InputType(null,null,null,rule);
	this.inputTypeContainer=this.inputType.getContainer();
	this.container.append(this.inputTypeContainer);
	this.afterContainer=$("<span>");
	RuleForge.setDomContent(this.afterContainer,"");
	this.container.append(this.afterContainer);
};
ruleforge.PrintAction.prototype.initData=function(data){
	if(!data){
		return;
	}
	var value=data["value"];
	if(!value){
		return;
	}
	this.inputType.setValueType(value["valueType"], value);
};
ruleforge.PrintAction.prototype.toXml=function(){
	var xml="<console-print>";
	xml+=this.inputType.toXml();
	xml+="</console-print>";
	return xml;
};
ruleforge.PrintAction.prototype.getContainer=function(){
	return this.container;
};