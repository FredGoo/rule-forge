import * as event from '../../components/componentEvent.js';

ruleforge.ResourceVersionDialog=function(path){
	this.path=path;
};
ruleforge.ResourceVersionDialog.prototype.open=function(doSuccess){
	var self=this;
	var url="ruleforge?action=loadversion&file="+this.path;
	fetch(url).then(function(response){
		if(!response.ok) throw response;
		return response.json();
	}).then(function(data){
		event.eventEmitter.emit(event.OPEN_RESOURCE_VERSION_DIALOG,{
			path:self.path,
			data:data || [],
			callback:doSuccess
		});
	}).catch(function(){
		RuleForge.alert("加载版本信息失败");
	});
};
