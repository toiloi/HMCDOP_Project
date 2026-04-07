import { BrowserRouter, Routes, Route, Navigate, NavLink, useNavigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage.jsx'
import DeployPage from './pages/DeployPage.jsx'
import AppsPage from './pages/AppsPage.jsx'
import NodesPage from './pages/NodesPage.jsx'

function Sidebar() {
  const navigate = useNavigate()
  const email = localStorage.getItem('email')

  function logout() {
    localStorage.clear()
    navigate('/login')
  }

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        🚀 MiniPaaS
        <span>Hybrid Multi-Cloud</span>
      </div>
      <nav className="sidebar-nav">
        <NavLink to="/deploy" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
          ⚡ Deploy Mới
        </NavLink>
        <NavLink to="/apps" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
          📦 Ứng dụng
        </NavLink>
        <NavLink to="/nodes" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
          🖥️ K3s Nodes
        </NavLink>
      </nav>
      <div style={{ padding: '16px 20px', borderTop: '1px solid var(--border)' }}>
        <div className="text-muted" style={{ marginBottom: 8 }}>{email}</div>
        <button className="btn btn-danger btn-sm" style={{ width: '100%' }} onClick={logout}>
          Đăng xuất
        </button>
      </div>
    </aside>
  )
}

function PrivateLayout({ children }) {
  if (!localStorage.getItem('token')) return <Navigate to="/login" replace />
  return (
    <div className="layout">
      <Sidebar />
      <main className="main">{children}</main>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/deploy" element={<PrivateLayout><DeployPage /></PrivateLayout>} />
        <Route path="/apps" element={<PrivateLayout><AppsPage /></PrivateLayout>} />
        <Route path="/nodes" element={<PrivateLayout><NodesPage /></PrivateLayout>} />
        <Route path="*" element={<Navigate to="/deploy" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
