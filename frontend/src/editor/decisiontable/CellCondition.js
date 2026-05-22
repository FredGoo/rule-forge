/**
 * @author GJ
 */
window._conditionId = 0;
ruleforge.CellCondition = function (element) {
    this.container = $(element);
    this.container.css({
        height: "40px",
        position: "relative"
    });
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
        dis = $("<span style='color:gray'>无</span>");
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