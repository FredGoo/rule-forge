ruleforge.MethodAction=function(rule){
	this.parameters=[];
	this.rule=rule;
	this.init();
};
ruleforge.MethodAction.prototype.init=function(){
	this.container=document.createElement("span");
	this.nameContainer=document.createElement("span");
	this.container.appendChild(this.nameContainer);
	this.nameContainer.style.color="darkblue";
};
ruleforge.MethodAction.prototype.initData=function(data){
	if(!data){
		return;
	}
	this.bean=data["beanId"];
	this.name=data["beanLabel"];
	this.method=data["methodName"];
	this.methodLabel=data["methodLabel"];
	var parameters=data["parameters"];
	this.parameterCount=0;
	if(parameters){
		this.parameterCount=parameters.length;
	}
	if(this.parameterCount===0){
        this.nameContainer.textContent = this.methodLabel;
		var parameterLabel=document.createElement("span");
		parameterLabel.style.color="gray";
		parameterLabel.textContent="(无参数)";
		this.container.appendChild(parameterLabel);
	}else{
        this.nameContainer.textContent = this.methodLabel+"(";
    }
	if(this.parameterCount==0){
		return;
	}
	for(var i=0;i<this.parameterCount;i++){
		var p=parameters[i];
		if(i>0){
			var comma=document.createElement("span");
			comma.textContent=";";
			this.container.appendChild(comma);
		}
		if(this.parameterCount>0){
			var seqLabel=document.createElement("span");
			seqLabel.style.color="purple";
			seqLabel.innerHTML="&nbsp;"+p["name"]+":";
			this.container.appendChild(seqLabel);
		}
		var parameter=new ruleforge.MethodParameter(this.rule);
		this.parameters.push(parameter);
		this.container.appendChild(parameter.getContainer());
		parameter.initData(p);
	}
	var rightParen=document.createElement("span");
	rightParen.textContent=")";
	this.container.appendChild(rightParen);
};
ruleforge.MethodAction.prototype.toXml=function(){
	if(!this.name || this.name==""){
		throw "请选择要执行的方法！";
	}
	var xml="<execute-method bean=\""+this.bean+"\" bean-label=\""+this.name+"\" method-label=\""+this.methodLabel+"\" method-name=\""+this.method+"\">";
	for(var i=0;i<this.parameters.length;i++){
		var p=this.parameters[i];
		xml+=p.toXml();
	}
	xml+="</execute-method>";
	return xml;
};
ruleforge.MethodAction.prototype.getContainer=function(){
	return this.container;
};