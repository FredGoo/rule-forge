import {createRoot} from 'react-dom/client';
import {BrowserRouter, Routes, Route, Navigate} from 'react-router-dom';
import LoginPage from '@/login';

/**
 * SPA 根入口(spa-migration-plan.md 阶段 1)。
 *
 * <p>根 {@code index.html} → 本文件 → {@link BrowserRouter}。
 * <ul>
 *   <li>{@code /} → {@code <Navigate to="/login"/>}</li>
 *   <li>{@code /login} → {@link LoginPage}</li>
 * </ul>
 *
 * <p>frame 仍是独立 {@code /html/frame.html}(阶段 2 路由化到 {@code /app})。
 * {@code login.html} 保留可独立访问(经 {@code src/login/main.tsx} 挂载)。
 */
createRoot(document.getElementById('root')!).render(
    <BrowserRouter>
        <Routes>
            <Route path="/" element={<Navigate to="/login" replace/>}/>
            <Route path="/login" element={<LoginPage/>}/>
        </Routes>
    </BrowserRouter>,
);
