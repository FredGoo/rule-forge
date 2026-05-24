window.Remark = function (container) {
    this.remark = "";
    this.defaultRemark = "请输入备注内容";
    this.container = container;
    this._collapsed = true;
    this._buildUI();
};

Remark.prototype._buildUI = function () {
    const container = this.container;

    const toolbar = document.createElement('div');
    toolbar.style.cssText = 'cursor:pointer;color:#777;font-size:12px';
    toolbar.textContent = '备注';

    this.icon = document.createElement('i');
    this.icon.className = 'glyphicon glyphicon-circle-arrow-right';
    toolbar.appendChild(this.icon);

    const _this = this;
    toolbar.addEventListener('click', function () {
        _this._collapsed = !_this._collapsed;
        if (_this._collapsed) {
            _this.contentContainer.style.display = 'none';
            _this.icon.classList.remove('glyphicon-circle-arrow-down');
            _this.icon.classList.add('glyphicon-circle-arrow-right');
        } else {
            _this.contentContainer.style.display = '';
            _this.icon.classList.remove('glyphicon-circle-arrow-right');
            _this.icon.classList.add('glyphicon-circle-arrow-down');
        }
    });
    container.appendChild(toolbar);

    this.contentContainer = document.createElement('div');
    this.contentContainer.style.display = 'none';
    container.appendChild(this.contentContainer);

    this.remarkLabel = document.createElement('div');
    this.remarkLabel.style.cssText = 'color:#999;background: #fdfdfd;padding:5px;border:solid 1px #ddd;border-radius: 5px;font-size: 12px';
    this.remarkLabel.textContent = this.defaultRemark;

    this.remarkLabel.addEventListener('click', function () {
        _this.remarkEditor.style.display = '';
        _this.remarkEditor.focus();
        _this.remarkLabel.style.display = 'none';
    });
    this.contentContainer.appendChild(this.remarkLabel);

    this.remarkEditor = document.createElement('textarea');
    this.remarkEditor.className = 'form-control';
    this.remarkEditor.rows = 4;
    this.remarkEditor.value = this.defaultRemark;
    this.remarkEditor.style.display = 'none';

    this.remarkEditor.addEventListener('change', function () {
        _this.remark = this.value;
        if (_this.remark === "") {
            _this.remarkLabel.textContent = _this.defaultRemark;
        } else {
            _this.remarkLabel.innerHTML = _this.parseBreak(_this.remark);
        }
        if (window.setDirty) {
            window.setDirty();
        }
        if (window._setDirty) {
            window._setDirty();
        }
    });
    this.remarkEditor.addEventListener('blur', function () {
        _this.remarkEditor.style.display = 'none';
        _this.remarkLabel.style.display = '';
    });
    this.contentContainer.appendChild(this.remarkEditor);
};

Remark.prototype.setData = function (data) {
    if (!data || data === "") {
        return;
    }
    this.remark = data;
    this.remarkEditor.value = data;
    this.remarkLabel.innerHTML = this.parseBreak(data);
};

Remark.prototype.toXml = function () {
    return "<remark><![CDATA[" + this.remark + "]]></remark>";
};

Remark.prototype.parseBreak = function (data) {
    data = data.replace(new RegExp("<", 'gm'), '&lt;');
    data = data.replace(new RegExp(">", 'gm'), '&gt;');
    data = data.replace(new RegExp("\n", 'gm'), '</br>');
    return data;
};
