(function () {
    if (!window.RuleForge) {
        window.RuleForge = {};
    }
    RuleForge.setDomContent = function (element, value) {
        element.textContent = value;
    };
    RuleForge.getDomContent = function (element) {
        return element.textContent;
    };
})();