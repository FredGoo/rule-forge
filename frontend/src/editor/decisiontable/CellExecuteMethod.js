ruleforge.CellExecuteMethod=function(element){
	this.parentContainer=element[0] || element;
	this.parentContainer.style.height="40px";
	this.parentContainer.style.width="100%";
	this.container=generateContainer();
//	this.container.prop("innerText","无");
	RuleForge.setDomContent(this.container,"无");
	this.container.style.color="gray";
	this.parentContainer.appendChild(this.container);
	window._ActionTypeArray.push(this);
	this.initMenu();
};
ruleforge.CellExecuteMethod.prototype.initMenu=function(actionLibraries){
	var data=window._ruleforgeEditorActionLibraries;
	if(actionLibraries){
		data=actionLibraries;
	}
	var self,onClick,config;
	self=this;
	onClick=function(menuItem){
		var parent=menuItem.parent.parent;
		self.setAction({
			beanLabel:parent.label,
			beanId:parent.name,
			methodLabel:menuItem.label,
			methodName:menuItem.name,
			parameters:menuItem.parameters
		});
	};
	config={menuItems:[]};
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
ruleforge.CellExecuteMethod.prototype.clean=function(){
	window._setDirty();
	if(this.action){
		this.action.getContainer().remove();
	}
	RuleForge.setDomContent(this.container,"无");
	this.container.style.color="gray";
	this.action=null;
};
ruleforge.CellExecuteMethod.prototype.setAction=function(data){
	window._setDirty();
	if(this.action){
		this.action.getContainer().remove();
	}
	this.action=new ruleforge.MethodAction();
	RuleForge.setDomContent(this.container,".");
	this.container.style.color="white";
	this.parentContainer.appendChild(this.action.getContainer());
	this.action.initData(data);
};
ruleforge.CellExecuteMethod.prototype.toXml=function(){
	if(this.action){
		return this.action.toXml();
	}
	return "";
};
