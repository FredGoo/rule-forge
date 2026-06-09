import { vi } from 'vitest';

export function setupMockServer() {
    // 不要清成空串,否则 node 24 内置 fetch 拒绝相对 URL。
    // 复用 setup 文件已设的 'http://localhost'。
    (window as any)._server = (window as any)._server || 'http://localhost';
    const mockResponses = new Map<any, any>();
    const fetchMock = vi.fn(async (url: string | URL | Request, options?: RequestInit) => {
        const urlStr = typeof url === 'string' ? url : url.toString();
        for (const [pattern, handler] of mockResponses.entries()) {
            if (typeof pattern === 'string' && urlStr.includes(pattern)) {
                return handler(options);
            }
            if (pattern instanceof RegExp && pattern.test(urlStr)) {
                return handler(options);
            }
        }
        return { ok: true, status: 200, json: async () => ({ status: true }), text: async () => '' };
    });
    (global as any).fetch = fetchMock;

    return {
        mockResponse(pattern: string | RegExp, data: any) {
            mockResponses.set(pattern, () => ({
                ok: true,
                status: 200,
                json: async () => data,
                text: async () => typeof data === 'string' ? data : JSON.stringify(data),
            }));
        },
        mockError(pattern: string | RegExp, status = 500) {
            mockResponses.set(pattern, () => ({
                ok: false,
                status,
                json: async () => ({ status: false, message: 'Server error' }),
                text: async () => 'Server error',
            }));
        },
        fetchMock,
    };
}

export function teardownMockServer() {
    delete (window as any)._server;
    (global as any).fetch = undefined;
}
