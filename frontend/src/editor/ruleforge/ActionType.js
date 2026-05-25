ruleforge.ActionType=function(parentContainer,rule){
	this.uuid=Math.uuid();
	parentContainer.id=this.uuid;
	this.rule=rule;
	this.parentContainer=parentContainer;
	this.type="";
	this.init();
	window._ActionTypeArray.push(this);
	this.initMenu();
};
ruleforge.ActionType.prototype.toXml=function(){
	if(this.type=="execute-function"){
		var xml="<execute-function ";
		xml+=this.action.toXml();
		xml+=">";
		xml+=this.action.getParameter().toXml();
		xml+="</execute-function>";
		return xml;
	}else{
		return this.action.toXml();
	}
};
ruleforge.ActionType.prototype.initData=function(data){
	if(!data){
		return;
	}
	var actionType=data["actionType"];
	this.setAction(actionType, data);
};

ruleforge.ActionType.prototype.init=function(){
	this.container=generateContainer();
	RuleForge.setDomContent(this.container,"请选择动作类型");
	this.container.style.color = "green";
	this.parentContainer.appendChild(this.container);
	this.action=null;
};
ruleforge.ActionType.prototype.initMenu=function(actionLibraries){
	var data=window._ruleforgeEditorActionLibraries;
	if(actionLibraries){
		data=actionLibraries;
	}
	var self,onClick,config;
	self=this;
	onClick=function(menuItem){
		var parent=menuItem.parent.parent;
		self.setAction("ExecuteMethod",{
			beanLabel:parent.label,
			beanId:parent.name,
			methodLabel:menuItem.label,
			methodName:menuItem.name,
			parameters:menuItem.parameters
		});
	};
	config={menuItems:[{
		label:"打印内容到控制台",
		onClick:function(){
			self.setAction("ConsolePrint");
		}
	},{
		label:"变量赋值",
		onClick:function(){
			self.setAction("VariableAssign");
		}
	},{
		label:"执行函数",
		onClick:function(){
			self.setAction("ExecuteCommonFunction");
		}
	}]};
	data||[].forEach(function(item) {
		var springBeans=item.springBeans||[];
		springBeans.forEach(function(springBean) {
			var menuItem={
				name:springBean.id,
				label:springBean.name
			}
			var methods=springBean.methods||[];
			methods.forEach(function(method) {
				if(!menuItem.subMenu){
					menuItem.subMenu={menuItems:[]};
				}
				menuItem.subMenu.menuItems.push({
					name:method.methodName,
					label:method.name,
					parameters:method.parameters,
					onClick:onClick
				});
			});

			config.menuItems.push(menuItem);
		});
	});
	if(self.menu){
		self.menu.setConfig(config);
	}else{
		self.menu=new RuleForge.menu.Menu(config);
	}
	this.container.addEventListener('click',function(e){
		self.menu.show(e);
	});
};

ruleforge.ActionType.prototype.initDefaultMenuData=function(){
	var self=this;
	var menuData=[];
	menuData.push({
		name:"打印内容到控制台",
		fun:function(){
			self.setAction("ConsolePrint");
		}
	});
	menuData.push({
		name:"变量赋值",
		fun:function(){
			self.setAction("VariableAssign");
		}
	});
	return menuData;
};

ruleforge.ActionType.prototype.setAction=function(type,data){
	window._setDirty();
	if(this.action){
		this.action.getContainer().remove();
	}
	switch(type){
	case "ConsolePrint":
		this.action=new ruleforge.PrintAction(this.rule);
		RuleForge.setDomContent(this.container,"输出:");
		this.type="console-print";
		break;
	case "ExecuteMethod":
		this.action=new ruleforge.MethodAction(this.rule);
		RuleForge.setDomContent(this.container,"执行方法:");
		this.type="execute-method";
		break;
	case "VariableAssign":
		this.action=new ruleforge.AssignmentAction(this.rule);
		RuleForge.setDomContent(this.container,"变量赋值:");
		this.type="var-assign";
		break;
	case "ExecuteCommonFunction":
		this.action=new ruleforge.FunctionValue(null,null,this.rule);
		RuleForge.setDomContent(this.container,"执行函数:");
		this.type="execute-function";
		break;
	}
	this.parentContainer.appendChild(this.action.getContainer());
	this.action.initData(data);
};