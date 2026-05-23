var ruleforge={};
var codeMirror=null;
$(document).ready(function() {
	CodeMirror.commands.autocomplete = function(cm) {
        cm.showHint({hint: CodeMirror.hint.ruleforge});
    };
	var codeEditor=document.getElementById("codeEditor");
	codeMirror = CodeMirror.fromTextArea(codeEditor, {
		lineNumbers: true,
	    mode: "rulemixed",
        extraKeys: {"Alt-/": "autocomplete"}
	});
	codeMirror.on("change",function(cm,e){
		var value = cm.getValue();
		if(e.text=="."){
			CodeMirror.commands.autocomplete(codeMirror);
		}
		$(".CodeMirror-code").find("pre").children(".error").removeClass("error");
		$(".CodeMirror-code").find("pre").attr("title", "");
		if(cm.validator){
			clearTimeout(cm.validator);
		}
		if($.trim(value)){
			cm.validator = setTimeout(function() {
				fetch("ruleforge?action=checkdsl", {
					method: "POST",
					headers: {'Content-Type': 'application/x-www-form-urlencoded'},
					body: new URLSearchParams({content: value}).toString()
				}).then(function(response) {
					if (!response.ok) throw response;
					return response.json();
				}).then(function(infos) {
					if(infos && infos.length>0){
						for(var i = 0; i<infos.length; i ++) {
							var pre = $(".CodeMirror-code").find("pre").eq(infos[i].line-1);
							pre.children("span").addClass("error");
							pre.attr("title",infos[i].message);

						}
					}
				}).catch(function() {
					RuleForge.alert("校验失败！");
				});
				cm.validator = null;
			}, 1000);
		}
	})
	init();
});

function init(){
	var height=$(document).height()-55;
	codeMirror.setSize("100%",height)
	window._dirty=false;
	var file=_getRequestParameter("file");
	if(!file || file.length<1){
		RuleForge.alert("当前编辑器未指定具体规则文件！");
		return;
	}
	var saveButton = '<div class="btn-group navbar-btn" style="margin-right:10px;" role="group" aria-label="...">'+
						'<button id="saveButton" type="button" class="btn btn-default navbar-btn" ><i class="icon-save"></i> 保存</button>' +
						'<button id="saveButtonNewVersion" type="button" class="btn btn-default navbar-btn" style="display: none;"><i class="icon-save"></i> 生成版本</button>' +
					'</div>';
	if(!hasPermission()) {
		saveButton = '';
	}else {
		$(window).keydown(function(event) {
			if(event.ctrlKey) {
				if(event.keyCode == 83) {
					save(file, false);
				}
			} else if (event.altKey) {
				if(event.keyCode == 83) {
					save(file, true);
				}
			}
		});
	}
	var toolbarHtml='<nav class="navbar navbar-default">'+
		'<div>'+
	        '<div class="collapse navbar-collapse">'+ saveButton +
	            '<button id="checkDSL" type="button" class="btn btn-default navbar-btn"><i class="icon-ok-sign"></i> 语法检查</button>'+
	            '<div class="btn-group navbar-btn" style="margin-left:10px;" role="group" aria-label="...">'+
	                '<button id="addVarButton" type="button" class="btn btn-default"><i class="icon-tasks"></i> 导入变量库</button>'+
	                '<button id="addConstantsButton" type="button" class="btn btn-default"><i class="icon-th-list"></i> 导入常量库</button>'+
	                '<button id="addActionButton" type="button" class="btn btn-default"><i class="icon-bolt"></i> 导入动作库</button>'+
	                '<button id="configParameterButton" type="button" class="btn btn-default"><i class="icon-th"></i> 导入参数库</button>'+
	            '</div>'+
	       ' </div>'+
	    '</div>'+
	'</nav>';
	var toolbar=$(toolbarHtml);
	toolbar.css({
		padding:"4px",
		diaplay:"inline-block"
	});
	$("#toolbarContainer").append(toolbar);
	$("#saveButton").click(function(){
		save(file,false);
	});
	$("#saveButtonNewVersion").click(function(){
		save(file,true);
	});
	$("#checkDSL").click(function(){
		checkDSL();
	});

	$("#configParameterButton").click(function(){
		var dialog=new ruleforge.ResourceListDialog("ParameterLibrary",null,selectResource);
		dialog.open();
	});
	$("#addVarButton").click(function(){
		var dialog=new ruleforge.ResourceListDialog("VariableLibrary",null,selectResource);
		dialog.open();
	});
	$("#addConstantsButton").click(function(){
		var dialog=new ruleforge.ResourceListDialog("ConstantLibrary",null,selectResource);
		dialog.open();
	});
	$("#addActionButton").click(function(){
		var dialog=new ruleforge.ResourceListDialog("ActionLibrary",null,selectResource);
		dialog.open();
	});

	window._dirty=false;
	var url="ruleforge?action=loaddsl&file="+file+"";
	fetch(url).then(function(response) {
		if (!response.ok) throw response;
		return response.text();
	}).then(function(data) {
		codeMirror.setValue(data);
		$("#saveButton").addClass("disabled");

		codeMirror.on("change",function(){
			setDirty();
		});
		loadResLib();
	}).catch(function() {
		RuleForge.alert("文件加载失败！");
	});
};

function selectResource(type,res){
	codeMirror.replaceSelection("import"+type+" \""+res+"\";");
	loadResLib();
};

function checkDSL(doSuccess){
	var content=codeMirror.getValue();
	if(!content || content.length<10){
		RuleForge.alert("请正确输入规则内容！");
		return;
	}
	var url="ruleforge?action=checkdsl";
	fetch(url, {
		method: "POST",
		headers: {'Content-Type': 'application/x-www-form-urlencoded'},
		body: new URLSearchParams({content: content}).toString()
	}).then(function(response) {
		if (!response.ok) throw response;
		return response.json();
	}).then(function(data) {
		if(!data || data.length <= 0){
			if(doSuccess){
				doSuccess();
			}else{
				RuleForge.alert("语法正确！");

			}
			return;
		}
		RuleForge.alert("语法不正确！");
	}).catch(function() {
		RuleForge.alert("语法检查失败！");
	});
};
function loadResLib(){
	var file=_getRequestParameter("file");
	var content=codeMirror.getValue();
	if(!content || content.length<10){
		RuleForge.alert("请正确输入规则内容！");
		return;
	}
	var url="ruleforge?action=loaddslreslib";
	fetch(url, {
		method: "POST",
		headers: {'Content-Type': 'application/x-www-form-urlencoded'},
		body: new URLSearchParams({content: content}).toString()
	}).then(function(response) {
		if (!response.ok) throw response;
		return response.json();
	}).then(function(data) {
		codeMirror._library=data;
	}).catch(function() {
		//alert("资源库加载失败!");
	});
};
function save(file,newVersion){
	if($("#saveButton").hasClass("disabled")){
		return false;
	}
	var url="ruleforge?action=savedsl&file="+file+"";
	var content=codeMirror.getValue();
	fetch(url, {
		method: "POST",
		headers: {'Content-Type': 'application/x-www-form-urlencoded'},
		body: new URLSearchParams({content: content, newVersion: newVersion}).toString()
	}).then(function(response) {
		if (!response.ok) throw response;
		return response.json();
	}).then(function() {
		cancelDirty();
	}).catch(function() {
		RuleForge.alert("保存失败！");
	});
};

function setDirty(){
	if(window._dirty){
		return;
	}
	window._dirty=true;
	$("#saveButton").html("<i class='icon-save'></i> *保存");
	$("#saveButton").removeClass("disabled");
};

function cancelDirty(){
	if(!window._dirty){
		return;
	}
	window._dirty=false;
	$("#saveButton").html("<i class='icon-save'></i> 保存");
	$("#saveButton").addClass("disabled");
};

function _getRequestParameter(name){
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
