import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Global test setup
// 必须有合法 origin,否则 node 24 内置 fetch 拒绝相对 URL
// (api/client.ts 的 baseUrl() 会把 path 拼到这个值上)
window._server = 'http://localhost';

// Antd Table / Grid 需要 window.matchMedia (jsdom 不提供)
Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
});

// Antd Tooltip/Popper needs getComputedStyle
const origGetComputedStyle = window.getComputedStyle;
window.getComputedStyle = function (elt, ...args) {
    const style = origGetComputedStyle.call(this, elt, ...args);
    // jsdom may return undefined for some properties; antd reads these
    return new Proxy(style, {
        get(target, prop) {
            const val = target[prop];
            if (val !== undefined) return val;
            if (prop === 'boxSizing') return 'border-box';
            return '';
        },
    });
};

// Mock bootbox globally
window.bootbox = {
    alert: vi.fn(),
    confirm: vi.fn(),
    prompt: vi.fn(),
    dialog: vi.fn(),
};
