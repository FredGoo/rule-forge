import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Global test setup
// 必须有合法 origin,否则 node 24 内置 fetch 拒绝相对 URL
// (api/client.ts 的 baseUrl() 会把 path 拼到这个值上)
window._server = 'http://localhost';

// Mock bootbox globally
window.bootbox = {
    alert: vi.fn(),
    confirm: vi.fn(),
    prompt: vi.fn(),
    dialog: vi.fn(),
};

// V5.17: Antd 6 的 responsiveObserver 用 window.matchMedia — jsdom 不提供
// 不 mock 会让所有用 Table/Drawer/Row/Col 的组件在 test mount 时抛
// "window.matchMedia is not a function"(见 antd/lib/_util/responsiveObserver.js)。
if (!window.matchMedia) {
    window.matchMedia = vi.fn().mockImplementation((query) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    }));
}

// V5.17: Antd Table 用 ResizeObserver 监听列宽 — jsdom 不提供
// 不 mock 会让 Table mount 时抛 "ResizeObserver is not defined"。
// 用 class 形式因为 @rc-component/resize-observer 用 `new ResizeObserver(cb)` 实例化。
if (!window.ResizeObserver) {
    window.ResizeObserver = class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
    };
}
