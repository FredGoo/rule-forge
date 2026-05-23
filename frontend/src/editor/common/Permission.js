isAdmin = function() {
	if(window._isAdmin === undefined) {
		var xhr = new XMLHttpRequest();
		xhr.open("POST", "ruleforge?action=checkadmin", false);
		xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
		xhr.send();
		if (xhr.status === 200) {
			window._isAdmin = JSON.parse(xhr.responseText);
		}
	}
	return window._isAdmin;
};

hasPermission = function(type) {	
	if(isAdmin()) {
		return true;
	}
	var permissions = getPermissions(type);
	for(var i = 0; i < permissions.length; i ++) {
		var p = permissions[i];
		if(p.granted == true){
			return true;
		}
		return false;
	}
	
	return false;
};

getPermissions = function(type) {
	var path = getRequestParameter("file");
	var xhr = new XMLHttpRequest();
	xhr.open("POST", "ruleforge?action=loadpermission", false);
	xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
	xhr.send(new URLSearchParams({
		path : path.substring(0, path.lastIndexOf("/")),
		type : type || "Mod" + getRequestParameter("type")
	}).toString());
	if (xhr.status === 200) {
		_permissions = JSON.parse(xhr.responseText) || [];
	}

	return _permissions;
};

getRequestParameter=function(name){
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