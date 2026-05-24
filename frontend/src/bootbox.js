(function () {
    var _backdrop = null;
    var _currentModal = null;

    function createBackdrop() {
        var backdrop = document.createElement('div');
        backdrop.className = 'modal-backdrop fade in';
        document.body.appendChild(backdrop);
        document.body.classList.add('modal-open');
        return backdrop;
    }

    function removeBackdrop() {
        if (_backdrop) {
            _backdrop.remove();
            _backdrop = null;
        }
        if (!_currentModal) {
            document.body.classList.remove('modal-open');
        }
    }

    function closeModal(modal, callback, value) {
        modal.style.display = 'none';
        modal.remove();
        _currentModal = null;
        removeBackdrop();
        if (callback) callback(value);
    }

    function showDialog(options) {
        var title = options.title || '';
        var message = options.message || '';
        var onEscape = options.onEscape !== false;
        var closeButton = options.closeButton !== false;
        var size = options.size || '';
        var buttons = options.buttons || {};
        var callback = options.callback;

        var modal = document.createElement('div');
        modal.className = 'modal fade in';
        modal.style.display = 'block';
        modal.setAttribute('role', 'dialog');
        modal.setAttribute('tabindex', '-1');

        var sizeClass = size === 'large' ? ' modal-lg' : size === 'small' ? ' modal-sm' : '';
        var btnKeys = Object.keys(buttons);

        var html = '<div class="modal-dialog' + sizeClass + '">' +
            '<div class="modal-content">';

        if (title || closeButton) {
            html += '<div class="modal-header">';
            if (closeButton) {
                html += '<button type="button" class="bootbox-close-button close" aria-hidden="true">&times;</button>';
            }
            html += '<h4 class="modal-title">' + title + '</h4></div>';
        }

        html += '<div class="modal-body"><div class="bootbox-body">' + message + '</div></div>';

        if (btnKeys.length > 0 || callback) {
            html += '<div class="modal-footer">';
            if (btnKeys.length > 0) {
                for (var i = 0; i < btnKeys.length; i++) {
                    var btn = buttons[btnKeys[i]];
                    html += '<button type="button" class="btn ' + (btn.className || 'btn-default') +
                        '" data-bb-handler="' + btnKeys[i] + '">' + (btn.label || btnKeys[i]) + '</button>';
                }
            } else if (callback) {
                html += '<button type="button" class="btn btn-primary bootbox-accept">OK</button>';
            }
            html += '</div>';
        }

        html += '</div></div>';
        modal.innerHTML = html;
        document.body.appendChild(modal);
        _currentModal = modal;
        _backdrop = createBackdrop();

        // Close button
        var closeBtn = modal.querySelector('.bootbox-close-button');
        if (closeBtn) {
            closeBtn.addEventListener('click', function () {
                closeModal(modal, callback);
            });
        }

        // Named buttons
        var btnEls = modal.querySelectorAll('[data-bb-handler]');
        for (var i = 0; i < btnEls.length; i++) {
            (function (el) {
                var handler = el.getAttribute('data-bb-handler');
                var btnConfig = buttons[handler];
                el.addEventListener('click', function () {
                    if (btnConfig && btnConfig.callback) {
                        var result = btnConfig.callback(el);
                        if (result === false) return;
                    }
                    closeModal(modal, callback);
                });
            })(btnEls[i]);
        }

        // Simple callback button
        var acceptBtn = modal.querySelector('.bootbox-accept');
        if (acceptBtn && !btnKeys.length) {
            acceptBtn.addEventListener('click', function () {
                closeModal(modal, callback);
            });
        }

        // Escape key
        if (onEscape) {
            var escHandler = function (e) {
                if (e.key === 'Escape') {
                    document.removeEventListener('keydown', escHandler);
                    closeModal(modal, callback);
                }
            };
            document.addEventListener('keydown', escHandler);
        }

        return {
            modal: function (action) {
                if (action === 'hide') {
                    closeModal(modal, callback);
                }
            }
        };
    }

    window.bootbox = {
        alert: function (msg, cb) {
            var title, message, callback;
            if (typeof msg === 'object') {
                title = msg.title || '';
                message = msg.message || '';
                callback = cb || msg.callback;
            } else {
                message = msg;
                callback = cb;
            }
            return showDialog({
                title: title,
                message: message,
                closeButton: false,
                buttons: {
                    ok: {
                        label: 'OK',
                        className: 'btn-primary',
                        callback: function () {
                            if (callback) callback();
                        }
                    }
                }
            });
        },

        confirm: function (msg, cb) {
            var title, message, callback;
            if (typeof msg === 'object') {
                title = msg.title || '';
                message = msg.message || '';
                callback = cb || msg.callback;
            } else {
                message = msg;
                callback = cb;
            }
            return showDialog({
                title: title,
                message: message,
                closeButton: false,
                buttons: {
                    cancel: {
                        label: 'Cancel',
                        className: 'btn-default',
                        callback: function () {
                            if (callback) callback(false);
                        }
                    },
                    confirm: {
                        label: 'OK',
                        className: 'btn-primary',
                        callback: function () {
                            if (callback) callback(true);
                        }
                    }
                }
            });
        },

        prompt: function (msg, cb) {
            var title, message, callback;
            if (typeof msg === 'object') {
                title = msg.title || '';
                message = msg.message || '';
                callback = cb || msg.callback;
            } else {
                message = msg;
                callback = cb;
            }
            return showDialog({
                title: title,
                message: '<form class="bootbox-form">' +
                    '<input class="bootbox-input bootbox-input-text form-control" autocomplete="off" type="text" />' +
                    '</form>',
                closeButton: false,
                buttons: {
                    cancel: {
                        label: 'Cancel',
                        className: 'btn-default',
                        callback: function () {
                            if (callback) callback(null);
                        }
                    },
                    confirm: {
                        label: 'OK',
                        className: 'btn-primary',
                        callback: function () {
                            var input = document.querySelector('.bootbox-input');
                            if (callback) callback(input ? input.value : null);
                        }
                    }
                }
            });
        },

        dialog: function (options) {
            return showDialog(options);
        },

        setDefaults: function () {}
    };
})();
