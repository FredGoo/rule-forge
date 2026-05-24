ruleforge.Connection = function (context, isJoin, parentJoin) {
    this.isJoin = isJoin;
    this.context = context;
    this.parentJoin = parentJoin;
};
ruleforge.Connection.prototype.drawPath = function (startX, startY, endX, endY) {
    this.startX = startX;
    this.endX = endX;
    if (this.isJoin) {
        this.startY = startY - 3;
        this.endY = endY + 2;
    } else {
        this.startY = startY - 3;
        this.endY = endY - 3;
    }
    this.path = this.context.getPaper().path(this.buildPathInfo());
    this.path.attr({'stroke': '#777'});
    if (this.isJoin) {
        this.initJoin();
    } else {
        this.initCondition();
    }
};
ruleforge.Connection.prototype.toXml = function () {
    var xml = "";
    if (this.isJoin) {
        xml = this.join.toXml();
    } else {
        xml = this.condition.toXml();
    }
    return xml;
};
ruleforge.Connection.prototype.initJoin = function () {
    this.join = new ruleforge.Join(this.context);
    this.join.init(this);
    var joinContainer = this.join.getContainer();
    var left = (this.endX + 10) + "px";
    var top = this.endY + "px";
    joinContainer.style.position = "absolute";
    joinContainer.style.left = left;
    joinContainer.style.top = top;
    this.context.getCanvas().append(joinContainer);
};

ruleforge.Connection.prototype.getDisplayContainer = function () {
    if (this.join) {
        return this.join.getDisplayContainer();
    } else {
        return this.condition.getDisplayContainer();
    }
};

ruleforge.Connection.prototype.remove = function () {
    this.path.remove();
    if (this.join) {
        this.join.getContainer().remove();
    } else {
        this.conditionContainer.remove();
    }
    window._setDirty();
};

ruleforge.Connection.prototype.initCondition = function () {
    this.conditionContainer = document.createElement("div");
    var left = (this.endX + 10) + "px";
    var top = this.endY + "px";
    this.conditionContainer.style.position = "absolute";
    this.conditionContainer.style.left = left;
    this.conditionContainer.style.top = top;
    this.condition = new ruleforge.Condition(this.conditionContainer);
    var del = document.createElement("i");
    del.className = "glyphicon glyphicon-trash";
    del.style.cssText = "color: #019dff;cursor: pointer;font-size: 9pt;padding-left:5px";
    var self = this;
    del.addEventListener('click', function () {
        self.parentJoin.removeConnection(self);
    });
    this.conditionContainer.appendChild(del);
    this.context.getCanvas().append(this.conditionContainer);
};
ruleforge.Connection.prototype.update = function (add) {
    var pathInfo = this.buildPathInfo();
    this.path.attr("path", pathInfo);
    var top = this.endY + "px";
    if (this.conditionContainer) {
        this.conditionContainer.style.top = top;
    } else {
        this.join.getContainer().style.top = top;
    }
    if (this.join) {
        this.join.resetItemPosition(0, add);
    }
};
ruleforge.Connection.prototype.getParentJoin = function () {
    return this.parentJoin;
};
ruleforge.Connection.prototype.getCondition = function () {
    return this.condition;
};
ruleforge.Connection.prototype.getJoin = function () {
    return this.join;
};
ruleforge.Connection.prototype.getStartX = function () {
    return this.startX;
};
ruleforge.Connection.prototype.getStartY = function () {
    return this.startY;
};
ruleforge.Connection.prototype.getEndX = function () {
    return this.endX;
};
ruleforge.Connection.prototype.getEndY = function () {
    return this.endY;
};
ruleforge.Connection.prototype.setStartX = function (startX) {
    this.startX = startX;
};
ruleforge.Connection.prototype.setStartY = function (startY) {
    this.startY = startY;
};
ruleforge.Connection.prototype.setEndX = function (endX) {
    this.endX = endX;
};
ruleforge.Connection.prototype.setEndY = function (endY) {
    this.endY = endY;
};
ruleforge.Connection.prototype.buildPathInfo = function () {
    var left = 10;
    var top = 8;
    return "M" + (this.startX + left) + "," + (this.startY + top) + " C" + (this.startX + left) + "," + (this.endY + top) + "," + (this.startX + left) + "," + (this.endY + top) + "," + (this.endX + left) + "," + (this.endY + top);
};
