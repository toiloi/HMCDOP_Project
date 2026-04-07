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
        <div className="login-title">
          <h1>🚀 MiniPaaS</h1>
          <p>Nền tảng Triển khai Tự động — Hybrid Multi-Cloud</p>
        </div>

        <div className="card">
          <div className="flex gap-2" style={{ marginBottom: 20 }}>
            <button
              className={'btn ' + (isLogin ? 'btn-primary' : '')}
              style={{ flex: 1, background: isLogin ? undefined : 'transparent',
                border: isLogin ? undefined : '1px solid var(--border)' }}
              onClick={() => { setIsLogin(true); setError('') }}
            >
              Đăng nhập
            </button>
            <button
              className={'btn ' + (!isLogin ? 'btn-primary' : '')}
              style={{ flex: 1, background: !isLogin ? undefined : 'transparent',
                border: !isLogin ? undefined : '1px solid var(--border)' }}
              onClick={() => { setIsLogin(false); setError('') }}
            >
              Đăng ký
            </button>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="label">Email</label>
              <input className="input" type="email" placeholder="you@example.com"
                value={email} onChange={e => setEmail(e.target.value)} required />
            </div>
            <div className="form-group">
              <label className="label">Mật khẩu</label>
              <input className="input" type="password" placeholder="••••••••"
                value={password} onChange={e => setPassword(e.target.value)} required />
            </div>

            {error && (
              <div className="alert-error" style={{ marginBottom: 12, padding: '8px 12px' }}>
                ❌ {error}
              </div>
            )}

            <button type="submit" className="btn btn-primary" disabled={loading}
              style={{ width: '100%', justifyContent: 'center' }}>
              {loading ? <><span className="spinner" /> Đang xử lý...</> :
                (isLogin ? '→ Đăng nhập' : '→ Tạo tài khoản')}
            </button>
          </form>
        </div>

        <p className="text-muted" style={{ textAlign: 'center', marginTop: 16 }}>
          K3s × Tailscale × Kaniko × Traefik
        </p>
      </div>
    </div>
  )
}
