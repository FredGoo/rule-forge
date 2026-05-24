(function () {
    if (window.jQuery) return;

    var _nsRegistry = new WeakMap();

    function JQLite(element) {
        this.length = 0;
        if (element instanceof HTMLElement || element === window) {
            this[0] = element;
            this.length = 1;
        }
    }

    JQLite.prototype = {
        on: function (event, handler) {
            var parts = event.split('.');
            var type = parts[0], ns = parts[1];
            if (this[0]) {
                this[0].addEventListener(type, handler);
                if (ns) {
                    var reg = _nsRegistry.get(this[0]);
                    if (!reg) { reg = {}; _nsRegistry.set(this[0], reg); }
                    if (!reg[ns]) reg[ns] = [];
                    reg[ns].push({type: type, handler: handler});
                }
            }
            return this;
        },
        off: function (event) {
            if (!this[0]) return this;
            var parts = event.split('.');
            var type = parts[0], ns = parts[1];
            if (ns) {
                var reg = _nsRegistry.get(this[0]);
                if (reg && reg[ns]) {
                    for (var i = reg[ns].length - 1; i >= 0; i--) {
                        var entry = reg[ns][i];
                        if (!type || entry.type === type) {
                            this[0].removeEventListener(entry.type, entry.handler);
                            reg[ns].splice(i, 1);
                        }
                    }
                    if (reg[ns].length === 0) delete reg[ns];
                }
            }
            return this;
        },
        data: function (key, value) {
            if (!this[0]) return undefined;
            if (arguments.length === 1) {
                return this[0]._jqData && this[0]._jqData[key];
            }
            if (!this[0]._jqData) this[0]._jqData = {};
            this[0]._jqData[key] = value;
            return this;
        },
        first: function () { return this; },
        is: function (selector) {
            if (!this[0]) return false;
            if (selector === ':disabled') return !!this[0].disabled;
            if (selector === ':hidden') return this[0].offsetParent === null;
            if (selector === ':visible') return this[0].offsetParent !== null;
            try { return this[0].matches(selector); } catch (e) { return false; }
        },
        find: function (selector) {
            return new JQLite(this[0] ? this[0].querySelector(selector) : null);
        },
        has: function (selector) {
            return this[0] ? !!this[0].querySelector(selector) : false;
        },
        parent: function () {
            return new JQLite(this[0] ? this[0].parentElement : null);
        },
        prop: function (name, value) {
            if (!this[0]) return undefined;
            if (arguments.length === 1) return this[0][name];
            this[0][name] = value;
            return this;
        }
    };

    function $(selector) {
        if (typeof selector === 'string') {
            return new JQLite(document.querySelector(selector));
        }
        return new JQLite(selector);
    }

    $.fn = JQLite.prototype;

    $.extend = function () {
        var target = arguments[0], deep = false, i = 1;
        if (typeof target === 'boolean') {
            deep = target;
            target = arguments[1];
            i = 2;
        }
        for (; i < arguments.length; i++) {
            var source = arguments[i];
            if (source) {
                var keys = Object.keys(source);
                for (var k = 0; k < keys.length; k++) {
                    var key = keys[k];
                    if (deep && $.isPlainObject(source[key])) {
                        target[key] = $.extend(true, target[key] || {}, source[key]);
                    } else if (deep && Array.isArray(source[key])) {
                        target[key] = $.extend(true, [], source[key]);
                    } else {
                        target[key] = source[key];
                    }
                }
            }
        }
        return target;
    };

    $.isPlainObject = function (obj) {
        return Object.prototype.toString.call(obj) === '[object Object]' && obj.constructor === Object;
    };

    $.when = function (val) {
        if (val && typeof val.then === 'function') return val;
        return Promise.resolve(val);
    };

    $.trim = function (str) { return str ? str.trim() : ''; };

    window.jQuery = window.$ = $;
})();
