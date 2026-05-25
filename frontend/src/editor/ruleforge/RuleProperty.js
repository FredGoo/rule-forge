ruleforge.RuleProperty=function(parent,name,defaultValue,editorType){
	this.parent=parent;
	this.value=defaultValue;
	this.editorType=editorType;
	this.container=document.createElement("span");
	this.container.className="rule-property";
	var nameContainer=document.createElement("span");
	this.name=name;
	var label=this.getLabel();
	nameContainer.textContent = label+"：";
	this.container.appendChild(nameContainer);
	var valueContainer=document.createElement("span");
	var valueLabel=generateContainer();
	if(defaultValue=="")defaultValue="无";
	valueLabel.style.color="#000";
	valueLabel.textContent = defaultValue;
	valueContainer.appendChild(valueLabel);
	this.container.appendChild(valueContainer);
	var editor=null;
	this.radioName=Math.uuid(15);
	this.yesRadio=null;
	this.noRadio=null;
	if(editorType==1){
		editor=document.createElement("input");
		editor.type="text";
		editor.size="30";
		editor.className="form-control rule-text-editor";
	}else if(editorType==2){
		editor=document.createElement("input");
		editor.type="datetime";
		editor.size="30";
		editor.className="form-control rule-text-editor";
		editor.title="日期格式为:yyyy-MM-dd HH:mm:ss，如2016-10-11 12:50:06";
	}else if(editorType==3){
		this.yesRadio=document.createElement("input");
		this.yesRadio.type="radio";
		this.yesRadio.value="是";
		this.yesRadio.name=this.radioName;
		this.noRadio=document.createElement("input");
		this.noRadio.type="radio";
		this.noRadio.value="否";
		this.noRadio.name=this.radioName;
	}
	var self=this;
	if(editorType!=3){
		editor.addEventListener('blur',function(){
			self.value=editor.value;
			editor.style.display='none';
			if(self.value==""){
				valueLabel.textContent = "无";
			}else{
				valueLabel.textContent = self.value;
			}
			valueLabel.style.display='';
			window._setDirty();
		});
		valueLabel.addEventListener('click',function(){
			valueLabel.style.display='none';
			editor.value=self.value;
			editor.style.display='';
			editor.focus();
		});
		this.container.appendChild(editor);
		editor.style.display='none';
		if(editorType==2){
			if(defaultValue!=="无"){
				var defaultDate=new Date(defaultValue);
				this.value=defaultValue;//formatDate(defaultDate,'Y-m-d H:m:s');
				valueLabel.textContent = this.value;
			}
		}
	}else{
		if(defaultValue==true){
			this.yesRadio.checked=true;
		}else{
			this.noRadio.checked=true;
		}
		this.yesRadio.addEventListener('change',function(){
			window._setDirty();
		});
		this.noRadio.addEventListener('change',function(){
			window._setDirty();
		});
		valueLabel.style.display='none';
		this.container.appendChild(this.yesRadio);
		this.container.appendChild(this.noRadio);
	}
	var del=document.createElement("i");
	del.className="glyphicon glyphicon-remove rule-property-del";
	del.addEventListener('click',function(){
		self.container.remove();
		var pos=self.parent.properties.indexOf(self);
		self.parent.properties.splice(pos,1);
		window._setDirty();
	});
	this.container.appendChild(del);
};
ruleforge.RuleProperty.prototype.getLabel=function(){
	var label="";
	if(this.name=="salience"){
		label="优先级";
	}else if(this.name=="loop"){
		label="允许循环触发";
	}else if(this.name=="effective-date"){
		label="生效日期";
	}else if(this.name=="expires-date"){
		label="失效日期";
	}else if(this.name=="enabled"){
		label="是否启用";
	}else if(this.name=="debug"){
		label="允许调试信息输出";
	}else if(this.name=="activation-group"){
		label="互斥组";
	}else if(this.name=="agenda-group"){
		label="执行组";
	}else if(this.name=="auto-focus"){
		label="自动获取焦点";
	}else if(this.name=="ruleflow-group"){
		label="规则流组";
	}
	return label;
};
ruleforge.RuleProperty.prototype.toXml=function(){
	var xml=this.name;
	if(this.editorType==3){
		if(this.yesRadio.checked){
			xml+="=\"true\"";
		}else{
			xml+="=\"false\"";
		}
	}else{
		if(!this.value || this.value==""){
			throw "请输入属性"+this.name+"的具体值!";
		}
		xml+="=\""+this.value+"\"";
	}
	return xml;
};
ruleforge.RuleProperty.prototype.getContainer=function(){
	return this.container;
};