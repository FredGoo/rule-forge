(function () {
    if (!window.RuleForge) {
        window.RuleForge = {};
    }
    RuleForge.menu = {};
    RuleForge.menu.AbstractMenu = function (config) {
    };
    RuleForge.menu.AbstractMenu.prototype = {
        fadeSpeed: 100,
        above: 'auto',
        preventDoubleContext: true,
        compress: false,
        createDom: function () {

        }, getDom: function () {
            if (!this._dom) {
                this._dom = this.createDom();
                $(this._dom).data("ref", this);
            }
            return this._dom;
        }, render: function (target) {
            if (!this._rendered) {
                if (target) {
                    $(target).append(this.getDom());
                } else {
                    $("body").append(this.getDom());
                }
            }
            this._rendered = true;
        }, setConfig: function (config) {
            this.remove();
            this.constructor.call(this, config);

        }, remove: function () {
            if (this._dom) {
                this._dom.remove();
                this._dom = null;
            }
            this._rendered = false;
        }
    };

    RuleForge.menu.Menu = function (config) {
        RuleForge.menu.Menu.prototype.superClass.call(this, config);
        $.extend(this, config);
    };

    RuleForge.menu.Menu.prototype = new RuleForge.menu.AbstractMenu();
    RuleForge.menu.Menu.prototype.superClass = RuleForge.menu.AbstractMenu;
    RuleForge.menu.Menu.prototype.constructor = RuleForge.menu.Menu;

    RuleForge.menu.Menu.prototype.createDom = function () {
        var compressed, dom, menuItems, ul;
        compressed = this.compress ? ' compressed-context' : '';
        ul = $("<ul class='dropdown-menu dropdown-context" + compressed + "' style='font-size:12px'></ul>");
        dom = ul[0];
        this._dom = dom;
        menuItems = this.menuItems;
        var self = this;
        if (menuItems.length > 20) {
            var searchContainer = $("<div style='margin-left: 2px;margin-right: 2px'>");
            searchContainer.append("<i class='glyphicon glyphicon-filter' style='color:#006600;margin-left: 2px;margin-right: 2px'></i>  ");
            this.search = $("<input type='text' class='form-control' placeholder='输入值后回车查询' style='width: 85%;display: inline-block;height: 26px;padding: 1px;font-size: 12px;'>");
            searchContainer.append(this.search);
            ul.append(searchContainer);
            this.search.click(function (e) {
                e.stopPropagation();
            });
            this.search.dblclick(function (e) {
                e.stopPropagation();
            });
            this.search.keypress(function (event) {
                var keynum = (event.keyCode ? event.keyCode : event.which);
                if (keynum !== '13' && keynum !== 13) {
                    return;
                }
                var value = $(this).val();
                if (self.oldSearchValue && self.oldSearchValue === value) {
                    return;
                }
                self.oldSearchValue = value;
                while (self.menuItems.length > 0) {
                    self.menuItems[0].remove();
                }
                for (var i = 0; i < menuItems.length; i++) {
                    var item = menuItems[i];
                    var label = item.label;
                    if (!value || value === "") {
                        self.addMenuItem(item);
                    } else if (label && label.indexOf(value) > -1) {
                        self.addMenuItem(item);
                    }
                }
            });
        }
        this.menuItems = [];
        for (var i = 0; i < menuItems.length; i++) {
            this.addMenuItem(menuItems[i]);
        }
        return dom;
    };

    RuleForge.menu.Menu.prototype.addMenuItem = function (menuItem) {
        var item;
        if (menuItem instanceof RuleForge.menu.MenuItem) {
            item = menuItem;
        } else {
            if (menuItem.$type) {
                item = eval("(RuleForge.menu." + menuItem.$type + "(menuItem))")
            } else {
                item = new RuleForge.menu.MenuItem(menuItem);
            }
        }
        item.parent = this;
        item.render(this.getDom());
        this.menuItems.push(item);
        return item;
    };

    RuleForge.menu.Menu.prototype.getMenuItem = function (nameOrIndex) {
        var target;
        for (var i = 0; i < this.menuItems.length; i++) {
            target = this.menuItems[i];
            if (typeof nameOrIndex === "string") {
                if (target.name === nameOrIndex) {
                    return target;
                }
            } else {
                return this.menuItems[nameOrIndex];
            }
            if (target.subMenu) {
                target = target.subMenu.getMenuItem(nameOrIndex);
                if (target) {
                    return target;
                }
            }
        }
    };

    RuleForge.menu.Menu.prototype.remove = function () {
        RuleForge.menu.Menu.prototype.superClass.prototype.remove.call(this);
        if (this.parent) {
            this.parent.subMenu = null;
            this.parent.getDom().removeClass("dropdown-submenu");
        }
    };

    RuleForge.menu.Menu.prototype.show = function (e) {
        e.preventDefault();
        e.stopPropagation();
        this.render();
        $('.modal').removeAttr('tabindex');
        var $dd = $(this.getDom());
        var target = $(e.target), z = 3;
        while (!target.is("body")) {
            var pz = target.css("z-index");
            if (!isNaN(pz) && pz !== '0') {
                z = parseInt(pz) + 1;
                break;
            }
            target = target.parent();
        }
        $dd.css("z-index", z);
        $('.dropdown-context:not(.dropdown-context-sub)').hide();
        if (typeof this.above == 'boolean' && this.above) {
            $dd.addClass('dropdown-context-up')
                .css({
                    top: e.pageY - 20 - $dd.height(),
                    left: e.pageX - 13
                }).fadeIn(this.fadeSpeed);
        } else if (typeof this.above == 'string' && this.above == 'auto') {
            $dd.removeClass('dropdown-context-up');
            var autoH = $dd.height() + 12;
            if ((e.pageY + autoH) > ($('html').height() + 10) && e.pageY > autoH) {
                $dd.addClass('dropdown-context-up').css({
                    top: e.pageY - 20 - autoH,
                    left: e.pageX - 13
                }).fadeIn(this.fadeSpeed);
            } else {
                $dd.css({
                    top: e.pageY + 10,
                    left: e.pageX - 13
                }).fadeIn(this.fadeSpeed);
            }
        }
        if (this.onShow) {
            this.onShow(this);
        }
    };

    RuleForge.menu.Menu.prototype.hide = function () {
        var $dom = $(this._dom);
        if ($dom.is(":visible")) {
            if (this.onHide) {
                this.onHide(this);
            }
            $dom.fadeOut(this.fadeSpeed, function () {
                $dom.css({display: ''}).find('.drop-left').removeClass('drop-left');
            });
            if (this.parent) {
                this.parent.parent.hide();
            }
        }

    };

    RuleForge.menu.MenuItem = function (config) {
        RuleForge.menu.MenuItem.prototype.superClass.call(this, config);
        $.extend(this, config);
    };

    RuleForge.menu.MenuItem.prototype = new RuleForge.menu.AbstractMenu();
    RuleForge.menu.MenuItem.prototype.superClass = RuleForge.menu.AbstractMenu;
    RuleForge.menu.MenuItem.prototype.constructor = RuleForge.menu.MenuItem;

    RuleForge.menu.MenuItem.prototype.createDom = function () {
        var $li, iconAndLabel, self;
        self = this;
        $li = $("<li style='cursor: default'></li>");
        this._dom = $li[0];
        if (this.icon) {
            iconAndLabel = "<i class='" + this.icon + "'></i> " + this.label;
        } else {
            iconAndLabel = this.label;
        }

        $li.on("mouseenter", function () {
            $li.siblings(".dropdown-submenu").each(function () {
                $(this).find("ul.dropdown-context-sub").each(function () {
                    var menu = $(this).data("ref");
                    $(this).fadeOut(menu.fadeSpeed);
                });
            });

        });

        if (this.type === "divider") {
            $li.addClass("divider");
            $li.append(iconAndLabel);
        } else if (this.type == "header") {
            $li.addClass("nav-header");
            $li.append(iconAndLabel);
        } else {
            $li.append("<a>" + iconAndLabel + "</a>");
            if (this.subMenu) {
                this.setSubMenu(this.subMenu);
            }
        }
        if (self.onClick) {
            if (this.parent && this.parent.search) {
                $li.click(function (e) {
                    e.stopPropagation();
                });
                $li.dblclick(function (e) {
                    self.onClick(self, {event: e});
                    e.preventDefault();
                    e.stopPropagation();
                    self.parent.hide();
                });
            } else {
                $li.click(function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    self.onClick(self, {event: e});
                    self.parent.hide();
                });
            }
        }
        return $li[0];
    };


    RuleForge.menu.MenuItem.prototype.setSubMenu = function (menu) {
        var dom, self;
        self = this;
        dom = self.getDom();
        if (menu instanceof RuleForge.menu.Menu) {
            self.subMenu = menu;
        } else {
            self.subMenu = new RuleForge.menu.Menu(menu);
        }
        self.subMenu.parent = this;
        $(dom).attr("class", "dropdown-submenu")
            .on("mouseenter", function () {
                var $sub = $(this).find(".dropdown-context-sub:first"),
                    subWidth = $sub.width(),
                    subLeft = $sub.offset().left,
                    collision = (subWidth + subLeft) > window.innerWidth;
                if (collision) {
                    $sub.addClass('drop-left');
                }
                $(self.subMenu.getDom()).fadeIn(self.subMenu.fadeSpeed);
            });
        this.subMenu.render(dom);
        $(this.subMenu.getDom()).addClass("dropdown-context-sub");
        return this.subMenu;

    };

    RuleForge.menu.MenuItem.prototype.remove = function () {
        RuleForge.menu.MenuItem.prototype.superClass.prototype.remove.call(this);
        var i;
        i = this.parent.menuItems.indexOf(this);
        this.parent.menuItems.splice(i, 1);
    };

    RuleForge.menu.MenuItem.prototype.show = function () {
        $(this._dom).show();
    };

    RuleForge.menu.MenuItem.prototype.hide = function () {
        $(this._dom).hide();
    };


    $(document).on('dblclick', 'html', function () {
        $('.dropdown-context').each(function () {
            var menu;
            menu = $(this).data("ref");
            menu.hide();
        })
    });

    if (RuleForge.menu.AbstractMenu.preventDoubleContext) {
        $(document).on('contextmenu', '.dropdown-context', function (e) {
            e.preventDefault();
        });
    }

})();