import Raphael from 'raphael';

ruleforge.Context = function (container, rule) {
    this.container = container;
    this.paper = new Raphael(this.container.get(0), "100%", "100%");
    this.rule = rule;
    this.rootJoin = null;
};
ruleforge.Context.prototype.putToNamedMap = function (referenceName, variableCategory) {
    this.rule.namedMap.set(referenceName, variableCategory);
};
ruleforge.Context.prototype.deleteFromNamedMap = function (referenceName) {
    this.rule.namedMap.delete(referenceName);
};
ruleforge.Context.prototype.getVariableCategoryFromNamedMap = function (referenceName) {
    return this.rule.namedMap.get(referenceName);
};
ruleforge.Context.prototype.getCanvas = function () {
    return this.container;
};
ruleforge.Context.prototype.getPaper = function () {
    return this.paper;
};
ruleforge.Context.prototype.setRootJoin = function (join) {
    this.rootJoin = join;
};
ruleforge.Context.prototype.getRootJoin = function () {
    return this.rootJoin;
};
ruleforge.Context.prototype.getTotalChildrenCount = function () {
    return this.rootJoin.getChildrenCount();
};