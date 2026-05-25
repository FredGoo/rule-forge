window._conditionId = 0;
ruleforge.CellCondition = function (element) {
    if (typeof element === 'string') {
        var temp = document.createElement('div');
        temp.innerHTML = element;
        this.container = temp.firstElementChild || temp;
    } else if (element && element.nodeType) {
        this.container = element;
    } else if (element && element[0]) {
        this.container = element[0];
    } else {
        this.container = element;
    }
    this.container.style.height = "40px";
    this.container.style.position = "relative";
    var context = new ruleforge.Context(this.container);
    this.join = new ruleforge.Join(context);
    this.join.init(null);
    this.join.initTopJoin(this.container);
    this.join.setType("and");
    this.id = window._conditionId++;
};
ruleforge.CellCondition.prototype.clean = function () {
    this.join.clean();
    window._setDirty();
};
ruleforge.CellCondition.prototype.getId = function () {
    return this.id;
};
ruleforge.CellCondition.prototype.renderTo = function (container) {
    container.append(this.container);
};
ruleforge.CellCondition.prototype.getDisplayContainer = function () {
    var dis = null;
    if (this.join) {
        dis = this.join.getDisplayContainer();
    }
    if (!dis) {
        dis = document.createElement("span");
        dis.style.cssText = "color:gray";
        dis.textContent = "无";
    }
    return dis;
};
ruleforge.CellCondition.prototype.initData = function (data) {
    if (this.join) {
        this.join.initData(data);
    }
};
ruleforge.CellCondition.prototype.toXml = function () {
    return this.join.toXml();
};