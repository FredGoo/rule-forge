(function ($) {
    if (!window.RuleForge) {
        window.RuleForge = {};
    }
    RuleForge.setDomContent = function (jqueryObject, value) {
        if (navigator.userAgent.indexOf("Firefox") > 0) {
            jqueryObject.prop("textContent", value);
        } else {
            jqueryObject.prop("innerText", value);
        }
    };
    RuleForge.getDomContent = function (jqueryObject) {
        if (navigator.userAgent.indexOf("Firefox") > 0) {
            return jqueryObject.prop("textContent");
        } else {
            return jqueryObject.prop("innerText");
        }
    };
})($);