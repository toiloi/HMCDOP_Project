import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/axios.js'

export default function LoginPage() {
  const navigate = useNavigate()
  const [isLogin, setIsLogin] = useState(true)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(e) {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const endpoint = isLogin ? '/auth/login' : '/auth/register'
      const { data } = await api.post(endpoint, { email, password })
      localStorage.setItem('token', data.token)
      localStorage.setItem('email', data.email)
      navigate('/deploy')
    } catch (err) {
      setError(err.response?.data?.error || 'Có lỗi xảy ra. Thử lại!')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-box">
        {/* Logo */}
        <div className="login-logo">
          <div className="login-logo-icon">🚀</div>
          <h1>MiniPaaS</h1>
          <p>Nền tảng Triển khai Tự động — Hybrid Multi-Cloud</p>
        </div>

        {/* Card */}
        <div className="login-card">
          {/* Tabs */}
          <div className="tab-group">
            <button
              id="tab-login"
              className={'tab-btn' + (isLogin ? ' active' : '')}
              onClick={() => { setIsLogin(true); setError('') }}>
              Đăng nhập
            </button>
            <button
              id="tab-register"
              className={'tab-btn' + (!isLogin ? ' active' : '')}
              onClick={() => { setIsLogin(false); setError('') }}>
              Đăng ký
            </button>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="label" htmlFor="login-email">Email</label>
              <input
                id="login-email"
                className="input"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </div>

            <div className="form-group">
              <label className="label" htmlFor="login-password">Mật khẩu</label>
              <input
                id="login-password"
                className="input"
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                autoComplete={isLogin ? 'current-password' : 'new-password'}
              />
            </div>

            {error && (
              <div className="alert alert-error" style={{ marginBottom: 16 }}>
                <span>⚠</span>
                <span>{error}</span>
              </div>
            )}

            <button
              id="btn-submit-login"
              type="submit"
              className="btn btn-primary btn-full btn-lg"
              disabled={loading}>
              {loading
                ? <><span className="spinner" /> Đang xử lý...</>
                : isLogin ? 'Đăng nhập →' : 'Tạo tài khoản →'}
            </button>
          </form>
        </div>

        {/* Footer chips */}
        <div className="login-footer">
          <span className="login-footer-chip">K3s</span>
          <span className="login-footer-chip">Tailscale</span>
          <span className="login-footer-chip">Kaniko</span>
          <span className="login-footer-chip">Traefik</span>
        </div>
      </div>
    </div>
  )
}
