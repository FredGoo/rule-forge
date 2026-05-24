ruleforge.SimpleValue = function (arithmetic, data) {
    var TIP = "请输入值";
    this.container = document.createElement("span");
    this.valueContainer = generateContainer();
    this.valueContainer.style.color = "rgb(180,95,4)";
    this.editor = document.createElement("input");
    this.editor.type = "text";
    this.editor.className = "form-control rule-text-editor";
    this.editor.style.height = "22px";
    var self = this;
    this.container.appendChild(this.valueContainer);
    this.container.appendChild(this.editor);
    this.editor.addEventListener("blur", function () {
        self.editor.style.display = "none";
        var text = self.editor.value;
        if (text != "") {
            RuleForge.setDomContent(self.valueContainer, text);
        }
        self.valueContainer.style.display = "";
        self.container.dispatchEvent(new Event("DOMSubtreeModified"));
        window._setDirty();
    });
    this.editor.addEventListener("mousedown", function (evt) {
        evt.stopPropagation();
    });
    this.editor.addEventListener("keydown", function (evt) {
        evt.stopPropagation();
    });
    self.editor.style.display = "none";
    this.valueContainer.innerText = TIP;
    this.valueContainer.addEventListener("click", function () {
        self.valueContainer.style.display = "none";
        var parent = self.container.parentElement;
        var maxWidth = 120;
        if (parent && parent.parentElement && parent.parentElement.parentElement) {
            parent = parent.parentElement.parentElement;
            var css = parent.className;
            if (css && css == "htMiddle htDimmed current") {
                maxWidth = parent.offsetWidth - 20;
            }
        }
        self.editor.style.width = maxWidth + "px";
        self.editor.style.display = "inline";
        self.editor.focus();
        self.container.dispatchEvent(new Event("DOMSubtreeModified"));
    });
    this.arithmetic = arithmetic;
    this.container.appendChild(arithmetic.getContainer());
    this.initData(data);
};

ruleforge.SimpleValue.prototype.getDisplayContainer = function () {
    var container = document.createElement("span");
    container.textContent = this.editor.value;
    if (this.arithmetic) {
        var dis = this.arithmetic.getDisplayContainer();
        if (dis) {
            container.appendChild(dis);
        }
    }
    return container;
};

ruleforge.SimpleValue.prototype.initData = function (data) {
    if (!data) {
        return;
    }
    var text = data["content"];
    //var disText=text.length>15?(text.substring(0,15)+"..."):text;
    RuleForge.setDomContent(this.valueContainer, text);
    this.editor.value = text;
    if (this.arithmetic) {
        this.arithmetic.initData(data["arithmetic"]);
    }
};
ruleforge.SimpleValue.prototype.getValue = function () {
    var value = this.editor.value;
    value = value.replace(new RegExp("&", "gm"), "&amp;");
    value = value.replace(new RegExp("<", "gm"), "&lt;");
    value = value.replace(new RegExp(">", "gm"), "&gt;");
    value = value.replace(new RegExp("'", "gm"), "&apos;");
    value = value.replace(new RegExp("\"", "gm"), "&quot;");
    return value;
};
ruleforge.SimpleValue.prototype.getContainer = function () {
    return this.container;
};