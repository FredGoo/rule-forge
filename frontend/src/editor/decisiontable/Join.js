ruleforge.Join = function (context) {
    this.type = "and";
    this.context = context;
    this.H = 30;
    this.W = 60;
    this.children = [];
    this.joinContainer = document.createElement("span");
    this.joinContainer.className = "btn btn-default dropdown-toggle";
    this.joinContainer.style.border = "solid gray 1px";
    this.joinContainer.style.padding = "3px";
    this.joinContainer.style.background = "#fff";
    this.joinLabel = document.createElement("span");
    this.joinLabel.textContent = "并且";
    this.joinContainer.appendChild(this.joinLabel);
};
ruleforge.Join.prototype.initData = function (data) {
    var conditions = [];
    var criterions = data["conditions"];
    var joints = data["joints"];
    this.setType(data["type"]);
    if (criterions) {
        conditions = criterions;
    }
    if (joints) {
        conditions = conditions.concat(joints);
    }
    if (conditions.length == 0) {
        return;
    }
    for (var i = 0; i < conditions.length; i++) {
        var criterion = conditions[i];
        var junctionType = criterion["type"];
        var isJoin = false;
        if (junctionType) {
            isJoin = true;
        }
        var newConnection = this.addItem(isJoin);
        if (isJoin) {
            newConnection.getJoin().initData(criterion);
        } else {
            newConnection.getCondition().initData(criterion);
        }
    }
};
ruleforge.Join.prototype.setType = function (type) {
    this.type = type;
    if (type == "or") {
        RuleForge.setDomContent(this.joinLabel, "或者");
    } else {
        RuleForge.setDomContent(this.joinLabel, "并且");
    }
    window._setDirty();
};
ruleforge.Join.prototype.init = function (parentConnection) {
    if (parentConnection) {
        this.parentConnection = parentConnection;
        this.parent = parentConnection.getParentJoin();
    }
    var joinArrow = document.createElement("i");
    joinArrow.className = "glyphicon glyphicon-chevron-down rule-join-node";
    var self = this;
    self.menu = new RuleForge.menu.Menu({
        menuItems: [{
            label: "并且",
            onClick: function () {
                self.setType("and");
            }
        }, {
            label: "或者",
            onClick: function () {
                self.setType("or");
            }
        }, {
            label: "添加条件",
            onClick: function () {
                self.addItem(false);
            }
        }]
    });
    this.joinContainer.addEventListener('click', function (e) {
        self.menu.show(e);
    });
    this.joinContainer.appendChild(joinArrow);
};
ruleforge.Join.prototype.clean = function () {
    while (this.children.length > 0) {
        var connection = this.children[0];
        this.removeConnection(connection);
    }
};

ruleforge.Join.prototype.removeConnection = function (connection) {
    var pos = this.children.indexOf(connection);
    if (this.children.length > 1) {
        this.resetItemPosition(pos + 1, false);
    }
    connection.remove();
    this.children.splice(pos, 1);
    this.resetContainerSize();
    window._setDirty();
};
ruleforge.Join.prototype.addItem = function (isJoin) {
    window._setDirty();
    var childrenCount = this.getChildrenCount();
    if (childrenCount > 0 && this.parent) {
        var parentChildren = this.parent.getChildren();
        var pos = parentChildren.indexOf(this.parentConnection);
        this.parent.resetItemPosition(pos + 1, true);
    }
    var totalHeight = childrenCount * this.H;
    var parentLeft = parseInt(this.joinContainer.style.left);
    var parentTop = parseInt(this.joinContainer.style.top);
    var startX = parentLeft + this.W / 2;
    var startY = parentTop + this.H / 5;
    var endX = startX + this.W - 25;
    var endY = startY + totalHeight;
    if (isJoin) {
        endY -= 5;
    }
    var connection = new ruleforge.Connection(this.context, isJoin, this);
    connection.drawPath(startX, startY, endX, endY);
    this.children.push(connection);
    this.resetContainerSize();
    return connection;
};
ruleforge.Join.prototype.toXml = function () {
    var xml = "<joint type=\"" + this.type + "\">";
    for (var i = 0; i < this.children.length; i++) {
        var conn = this.children[i];
        xml += conn.toXml();
    }
    xml += "</joint>";
    return xml;
};
ruleforge.Join.prototype.resetItemPosition = function (index, add) {
    if (index == -1) {
        return;
    }
    for (var i = index; i < this.children.length; i++) {
        var connection = this.children[i];
        var offset = this.H;
        if (!add) {
            offset = -this.H;
        }
        connection.setEndY(connection.getEndY() + offset);
        if (index == 0) {
            connection.setStartY(connection.getStartY() + offset);
        }
        connection.update(add);
    }
    if (index > 0 && this.parent) {
        var parentChildren = this.parent.getChildren();
        var pos = parentChildren.indexOf(this.parentConnection);
        var parentJoin = this.parentConnection.getParentJoin();
        parentJoin.resetItemPosition(pos + 1, add);
    }
    window._setDirty();
};
ruleforge.Join.prototype.resetContainerSize = function () {
    var container = this.context.getCanvas();
    var height = container.style.height;
    height = parseInt(height);
    var childrenCount = this.context.getTotalChildrenCount();
    if (childrenCount == 0) childrenCount = 1;
    var totalHeight = childrenCount * this.H + 10;
    container.style.height = totalHeight + "px";
};
ruleforge.Join.prototype.getChildrenCount = function () {
    var total = 0;
    for (var i = 0; i < this.children.length; i++) {
        var child = this.children[i].getJoin();
        if (child) {
            var count = child.getChildrenCount();
            if (count == 0) {
                count = 1;
            }
            total += count;
        } else {
            total++;
        }
    }
    return total;
};
ruleforge.Join.prototype.initTopJoin = function (container) {
    var left = 5;
    var top = 5;
    this.joinContainer.style.position = "absolute";
    this.joinContainer.style.left = left + "px";
    this.joinContainer.style.top = top + "px";
    container.appendChild(this.joinContainer);
    this.context.setRootJoin(this);
};
ruleforge.Join.prototype.getDisplayContainer = function () {
    if (this.children.length == 0) {
        return null;
    }
    var container = document.createElement("span");
    for (var i = 0; i < this.children.length; i++) {
        var child = this.children[i];
        var childDisplayContainer = child.getDisplayContainer();
        if (!childDisplayContainer) {
            continue;
        }
        if (i > 0) {
            if (this.type == "or") {
                var orSpan = document.createElement("span");
                orSpan.style.cssText = "color:green";
                orSpan.textContent = " 或 ";
                container.appendChild(orSpan);
            } else {
                var andSpan = document.createElement("span");
                andSpan.style.cssText = "color:red";
                andSpan.textContent = " 并且 ";
                container.appendChild(andSpan);
            }
        }
        container.appendChild(childDisplayContainer);
    }
    return container;
};

ruleforge.Join.prototype.getChildren = function () {
    return this.children;
};
ruleforge.Join.prototype.getContainer = function () {
    return this.joinContainer;
};
