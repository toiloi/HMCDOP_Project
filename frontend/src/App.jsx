import { BrowserRouter, Routes, Route, Navigate, NavLink, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import LoginPage from './pages/LoginPage.jsx'
import DeployPage from './pages/DeployPage.jsx'
import AppsPage from './pages/AppsPage.jsx'
import NodesPage from './pages/NodesPage.jsx'

/* ── SVG Icons (inline, no dep) ── */
function IconRocket() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09z"/>
      <path d="m12 15-3-3a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.35 22.35 0 0 1-4 2z"/>
      <path d="M9 12H4s.55-3.03 2-4c1.62-1.08 5 0 5 0"/>
      <path d="M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5"/>
    </svg>
  )
}
function IconBox() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/>
      <path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/>
    </svg>
  )
}
function IconServer() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect width="20" height="8" x="2" y="2" rx="2" ry="2"/><rect width="20" height="8" x="2" y="14" rx="2" ry="2"/>
      <line x1="6" x2="6.01" y1="6" y2="6"/><line x1="6" x2="6.01" y1="18" y2="18"/>
    </svg>
  )
}
function IconRefresh() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
      <path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
      <path d="M8 16H3v5"/>
    </svg>
  )
}
function IconLogout() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
      <polyline points="16 17 21 12 16 7"/><line x1="21" x2="9" y1="12" y2="12"/>
    </svg>
  )
}
function IconMenu() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="4" x2="20" y1="12" y2="12"/><line x1="4" x2="20" y1="6" y2="6"/><line x1="4" x2="20" y1="18" y2="18"/>
    </svg>
  )
}
function IconX() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 6 6 18"/><path d="m6 6 12 12"/>
    </svg>
  )
}

/* ── Navbar ── */
function Navbar() {
  const navigate = useNavigate()
  const email = localStorage.getItem('email') || ''
  const [mobileOpen, setMobileOpen] = useState(false)

  function logout() {
    localStorage.clear()
    setMobileOpen(false)
    navigate('/login')
  }

  const avatarLetter = email ? email[0].toUpperCase() : 'U'

  return (
    <>
      <nav className="navbar">
        {/* Brand */}
        <div className="navbar-brand">
          <div className="navbar-logo-icon">🚀</div>
          <span>MiniPaaS</span>
        </div>

        <div className="navbar-divider" />

        {/* Desktop nav links */}
        <div className="navbar-nav">
          <NavLink to="/deploy" id="nav-deploy"
            className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
            <IconRocket /> Deploy
          </NavLink>
          <NavLink to="/apps" id="nav-apps"
            className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
            <IconBox /> Applications
          </NavLink>
          <NavLink to="/nodes" id="nav-nodes"
            className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>
            <IconServer /> K3s Nodes
          </NavLink>
        </div>

        {/* Right side */}
        <div className="navbar-right">
          <span className="user-email">{email}</span>
          <button id="btn-logout-desktop" className="btn-icon" onClick={logout} title="Đăng xuất">
            <IconLogout />
          </button>
          <div className="avatar" title={email}>{avatarLetter}</div>

          {/* Hamburger */}
          <button className="btn-icon" id="btn-hamburger"
            style={{ display: 'none', border: 'none', background: 'transparent' }}
            onClick={() => setMobileOpen(v => !v)}
            aria-label="Menu">
            {mobileOpen ? <IconX /> : <IconMenu />}
          </button>
          <style>{`
            @media (max-width: 640px) {
              #btn-hamburger { display: inline-flex !important; }
              #btn-logout-desktop { display: none; }
            }
          `}</style>
        </div>
      </nav>

      {/* Mobile overlay menu */}
      <div className={`mobile-menu ${mobileOpen ? 'open' : ''}`}>
        <NavLink to="/deploy" id="nav-deploy-mobile"
          className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}
          onClick={() => setMobileOpen(false)}>
          <IconRocket /> Deploy
        </NavLink>
        <NavLink to="/apps" id="nav-apps-mobile"
          className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}
          onClick={() => setMobileOpen(false)}>
          <IconBox /> Applications
        </NavLink>
        <NavLink to="/nodes" id="nav-nodes-mobile"
          className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}
          onClick={() => setMobileOpen(false)}>
          <IconServer /> K3s Nodes
        </NavLink>

        <div className="mobile-menu-footer">
          <span className="text-muted text-sm">{email}</span>
          <button id="btn-logout-mobile" className="btn btn-secondary btn-sm" onClick={logout}>
            <IconLogout /> Đăng xuất
          </button>
        </div>
      </div>
    </>
  )
}

/* ── Private layout ── */
function PrivateLayout({ children }) {
  if (!localStorage.getItem('token')) return <Navigate to="/login" replace />
  return (
    <div className="layout">
      <Navbar />
      <div className="page-wrapper">
        {children}
      </div>
    </div>
  )
}

/* ── App ── */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/deploy" element={<PrivateLayout><DeployPage /></PrivateLayout>} />
        <Route path="/apps"   element={<PrivateLayout><AppsPage /></PrivateLayout>} />
        <Route path="/nodes"  element={<PrivateLayout><NodesPage /></PrivateLayout>} />
        <Route path="*"       element={<Navigate to="/deploy" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
