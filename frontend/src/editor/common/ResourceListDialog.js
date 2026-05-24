import * as event from '../../components/componentEvent.js';

ruleforge.ResourceListDialog=function(type,select,doSuccess){
	this.type=type;
	this.select=select;
	this.doSuccess=doSuccess;
};
ruleforge.ResourceListDialog.prototype.open=function(){
	var self=this;
	event.eventEmitter.emit(event.OPEN_RESOURCE_LIST_DIALOG, {
		type: this.type,
		file: this.getRequestParameter("file"),
		callback: function(type, fullPath) {
			if (self.doSuccess) {
				self.doSuccess(type, fullPath);
			} else {
				self.doSelectFile(fullPath);
			}
		}
	});
};
ruleforge.ResourceListDialog.prototype.doSelectFile=function(selectedFile){
	var fullPath="jcr:"+selectedFile;
	if(this.doSuccess){
		this.doSuccess(this.type,fullPath);
		return;
	}
	var dup=false;
	if (this.select) {
		this.select.forEach(function(el){
			el.childNodes.forEach(function(node){
				var path=node.textContent;
				if(path==fullPath){
					dup=true;
				}
			});
		});
	}
	if(!dup){
		var self=this;
		var item=document.createElement("a");
		item.href="javascript:void(0)";
		item.className="list-group-item";
		item.textContent=fullPath;
		item.addEventListener("click", function(){
			if (self.select) {
				self.select.querySelectorAll(".active").forEach(function(el){
					el.classList.remove("active");
				});
			}
			item.classList.add("active");
		});
		if (this.select) {
			this.select.appendChild(item);
		}
	}else{
		RuleForge.alert("当前库文件已被添加！");
	}
};

ruleforge.ResourceListDialog.prototype.getRequestParameter=function(name){
	var value=null;
	var params=window.location.search.substring(1).split("&");
	for(var i=0;i<params.length;i++){
		var param=params[i];
		if(param.indexOf("=")==-1){
			continue;
		}
		var pair=param.split("=");
		var key=pair[0];
		if(key==name){
			value=pair[1];
			break;
		}
	}

	return value;
};
