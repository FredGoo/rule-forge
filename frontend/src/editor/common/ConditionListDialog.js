import * as event from '../../components/componentEvent.js';

ruleforge.ConditionListDialog=function(project, category, colData){
	this.project = project;
	this.category = category;
	this.colData = colData;
	this.variable = "";
};

ruleforge.ConditionListDialog.prototype.open=function(doSuccess){
	var self=this;
	var url="ruleforge?action=loadcommonconditions&project="+this.project + "&category=" + this.category + "&variable=" + this.colData.variableCategory + "." + this.colData.variableLabel;
	fetch(url).then(function(response){
		if(!response.ok) throw response;
		return response.json();
	}).then(function(data){
		self.variable=self.colData.variableCategory + "." + self.colData.variableLabel;
		event.eventEmitter.emit(event.OPEN_CONDITION_LIST_DIALOG,{
			variable:self.variable,
			data:data || [],
			callback:doSuccess
		});
	}).catch(function(){
		RuleForge.alert("加载常用条件失败");
	});
};

ruleforge.ConditionListDialog.prototype.setOption=function(option){
	// Dialog options are now handled by the React component
};

ruleforge.ConditionListDialog.prototype.refresh=function(project, category, variable){
	var self=this;
	event.eventEmitter.emit(event.REFRESH_CONDITION_LIST_DIALOG,{
		project:project || this.project,
		category:category || this.category,
		variable:this.colData.variableCategory + "." + this.colData.variableLabel
	});
};
