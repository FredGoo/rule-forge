import Sortable from 'sortablejs';

function RuleFactory(container) {
    this.container = container;
    container._dirty = false;
    container.rules = [];
    this.file = null;

    var remarkContainer = document.createElement("div");
    remarkContainer.style.margin = "5px";
    remarkContainer.style.padding = "5px";
    container.appendChild(remarkContainer);
    container.remark = new Remark(remarkContainer);

    var _this = container;
    Sortable.create(container, {
        delay: 200,
        onEnd: function (evt) {
            if (evt.oldIndex !== evt.newIndex) {
                var children = _this.querySelectorAll("div");
                children.forEach(function (div, index) {
                    var id = div.id, rules = _this.rules, targetRule = null;
                    for (let rule of rules) {
                        if (rule.uuid === id) {
                            targetRule = rule;
                            break;
                        }
                    }
                    if (targetRule) {
                        const pos = rules.indexOf(targetRule);
                        rules.splice(pos, 1);
                        rules.splice(index, 0, targetRule);
                    }
                });
                window._setDirty();
            }
        }
    });
}

RuleFactory.prototype.setFile = function (file) {
    this.file = file;
};

RuleFactory.prototype.addRule = function (data) {
    var self = this.container;
    var ruleContainer = document.createElement("div");
    ruleContainer.className = "well";
    ruleContainer.style.margin = "5px";
    ruleContainer.style.padding = "8px";
    ruleContainer.style.backgroundColor = "#fdfdfd";
    self.appendChild(ruleContainer);
    var rule = new ruleforge.Rule(self, ruleContainer, data);
    self.rules.push(rule);
    window._setDirty();
    return rule;
};

RuleFactory.prototype.addLoopRule = function (data) {
    var self = this.container;
    var ruleContainer = document.createElement("div");
    ruleContainer.className = "well";
    ruleContainer.style.margin = "5px";
    ruleContainer.style.padding = "8px";
    ruleContainer.style.borderColor = "#337AB7";
    ruleContainer.style.backgroundColor = "#fdfdfd";
    self.appendChild(ruleContainer);
    var rule = new ruleforge.LoopRule(self, ruleContainer, data);
    self.rules.push(rule);
    window._setDirty();
    return rule;
};

RuleFactory.prototype.toXml = function () {
    var self = this.container;
    var xml = '<?xml version="1.0" encoding="UTF-8"?>';
    xml += '<rule-set>';
    parameterLibraries.forEach(function (item) {
        xml += '<import-parameter-library path="' + item + '"/>';
    });
    variableLibraries.forEach(function (item) {
        xml += '<import-variable-library path="' + item + '"/>';
    });
    constantLibraries.forEach(function (item) {
        xml += '<import-constant-library path="' + item + '"/>';
    });
    actionLibraries.forEach(function (item) {
        xml += '<import-action-library path="' + item + '"/>';
    });
    xml += self.remark.toXml();
    for (var i = 0; i < self.rules.length; i++) {
        xml += self.rules[i].toXml();
    }
    xml += '</rule-set>';
    return xml;
};

RuleFactory.prototype.loadData = function (ruleset) {
    var self = this.container;
    var libraries = ruleset['libraries'];
    self.remark.setData(ruleset['remark']);
    if (libraries) {
        for (var i = 0; i < libraries.length; i++) {
            var lib = libraries[i];
            var type = lib['type'];
            var path = lib['path'];
            switch (type) {
                case 'Constant':
                    constantLibraries.push(path);
                    break;
                case 'Action':
                    actionLibraries.push(path);
                    break;
                case 'Variable':
                    variableLibraries.push(path);
                    break;
                case 'Parameter':
                    parameterLibraries.push(path);
                    break;
            }
        }
    }
    refreshActionLibraries();
    refreshConstantLibraries();
    refreshVariableLibraries();
    refreshParameterLibraries();
    refreshFunctionLibraries();
    var rules = ruleset['rules'];
    for (var i = 0; i < rules.length; i++) {
        var rule = rules[i];
        if (rule.loopRule) {
            this.addLoopRule(rule);
        } else {
            this.addRule(rule);
        }
    }
};

window.RuleFactory = RuleFactory;
