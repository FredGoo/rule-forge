import {createRoot} from 'react-dom/client';
import {createElement} from 'react';

var rootCache = new WeakMap();

export function renderReact(Component, props, container) {
    var root = rootCache.get(container);
    if (!root) {
        root = createRoot(container);
        rootCache.set(container, root);
    }
    root.render(createElement(Component, props));
}

export function unmountReact(container) {
    var root = rootCache.get(container);
    if (root) {
        root.unmount();
        rootCache.delete(container);
    }
}
