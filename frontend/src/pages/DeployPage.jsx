import { useState, useEffect, useRef } from 'react'
import api from '../api/axios.js'

export default function DeployPage() {
  const [githubUrl, setGithubUrl] = useState('')
  const [branch, setBranch] = useState('main')
  const [port, setPort] = useState(8080)
  const [loading, setLoading] = useState(false)
  const [logs, setLogs] = useState([])
  const [status, setStatus] = useState(null) // null | 'BUILDING' | 'RUNNING' | 'FAILED'
  const [resultUrl, setResultUrl] = useState('')
  const logEndRef = useRef(null)

  // Auto-scroll log viewer
  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [logs])

  function classifyLog(line) {
    if (line.includes('✅') || line.includes('thành công') || line.includes('SUCCESS'))
      return 'success'
    if (line.includes('❌') || line.includes('LỖI') || line.includes('ERROR') || line.includes('FAIL'))
      return 'error'
    if (line.includes('🔨') || line.includes('📦') || line.includes('INFO') || line.includes('>>>'))
      return 'info'
    return ''
  }

  async function handleDeploy(e) {
    e.preventDefault()
    setLogs([])
    setStatus('BUILDING')
    setResultUrl('')
    setLoading(true)

    try {
      // 1. Tạo deployment
      const { data: dep } = await api.post('/deployments', { githubUrl, branch, port })
      const depId = dep.id

      addLog(`📋 Deployment ID: ${depId}`)
      addLog(`🔗 Repository: ${githubUrl}`)
      addLog(`🌿 Branch: ${branch}`)
      addLog('')

      // 2. Kết nối SSE để nhận logs real-time
      const token = localStorage.getItem('token')
      const evtSource = new EventSource(
        `/api/v1/deployments/${depId}/logs?token=${token}`
      )

      evtSource.addEventListener('log', e => {
        addLog(e.data)
      })

      evtSource.addEventListener('status', e => {
        const [statusStr, url] = e.data.split(':')
        setStatus(statusStr)
        if (url) setResultUrl(url)
        evtSource.close()
        setLoading(false)
      })

      evtSource.onerror = () => {
        evtSource.close()
        setLoading(false)
      }

    } catch (err) {
      const msg = err.response?.data?.error || err.message
      addLog(`❌ Lỗi: ${msg}`)
      setStatus('FAILED')
      setLoading(false)
    }
  }

  function addLog(line) {
    setLogs(prev => [...prev, line])
  }

  function reset() {
    setLogs([])
    setStatus(null)
    setResultUrl('')
    setGithubUrl('')
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">⚡ Deploy Ứng dụng Mới</h1>
        <p className="page-subtitle">
          Nhập GitHub URL — hệ thống sẽ tự động build và deploy lên K3s cluster
        </p>
      </div>

      {/* Deploy Form */}
      {status === null && (
        <div className="card" style={{ maxWidth: 640 }}>
          <h2 className="card-title">📌 Thông tin Deploy</h2>
          <form onSubmit={handleDeploy}>
            <div className="form-group">
              <label className="label">GitHub URL (public repo) *</label>
              <input className="input" type="url" required
                placeholder="https://github.com/user/my-app"
                value={githubUrl} onChange={e => setGithubUrl(e.target.value)} />
              <p className="text-muted" style={{ marginTop: 4 }}>
                Repository phải có Dockerfile ở thư mục gốc
              </p>
            </div>

            <div className="grid-2">
              <div className="form-group">
                <label className="label">Branch</label>
                <input className="input" type="text" placeholder="main"
                  value={branch} onChange={e => setBranch(e.target.value)} />
              </div>
              <div className="form-group">
                <label className="label">Port ứng dụng</label>
                <input className="input" type="number" min={1} max={65535}
                  value={port} onChange={e => setPort(Number(e.target.value))} />
              </div>
            </div>

            <div style={{ marginTop: 8 }}>
              <div className="card" style={{ background: 'var(--bg)', marginBottom: 16, padding: '12px 16px' }}>
                <p className="text-muted" style={{ fontSize: 13 }}>
                  <strong style={{ color: 'var(--text)' }}>Luồng xử lý:</strong><br />
                  1️⃣ Clone repo từ GitHub → 2️⃣ Kaniko build Docker image → 3️⃣ Push lên GHCR →
                  4️⃣ K3s deploy → 5️⃣ Traefik cấp URL public ✅
                </p>
              </div>
              <button type="submit" className="btn btn-primary"
                style={{ width: '100%', justifyContent: 'center', padding: '10px' }}>
                🚀 Bắt đầu Deploy
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Log Viewer (khi đang build hoặc xong) */}
      {status !== null && (
        <div style={{ maxWidth: 800 }}>
          <div className="flex-between" style={{ marginBottom: 12 }}>
            <div className="flex gap-2" style={{ alignItems: 'center' }}>
              <h2 style={{ fontSize: 16, fontWeight: 600 }}>📋 Build Logs</h2>
              {loading && <span className="badge badge-building">
                <span className="spinner" style={{ width: 10, height: 10 }} /> Building...
              </span>}
              {status === 'RUNNING' && <span className="badge badge-running">✅ Thành công</span>}
              {status === 'FAILED' && <span className="badge badge-failed">❌ Thất bại</span>}
            </div>
            <button className="btn btn-sm" style={{ border: '1px solid var(--border)', background: 'transparent' }}
              onClick={reset}>
              ← Deploy mới
            </button>
          </div>

          <div className="log-viewer">
            {logs.map((line, i) => (
              <div key={i} className={`log-line ${classifyLog(line)}`}>
                {line || ' '}
              </div>
            ))}
            {loading && <div className="log-line info">▌<span className="log-cursor" /></div>}
            <div ref={logEndRef} />
          </div>

          {/* Result URL */}
          {status === 'RUNNING' && resultUrl && (
            <div className="alert-success" style={{ marginTop: 16 }}>
              <strong>🎉 Deploy thành công!</strong>
              <br />
              <a href={resultUrl} target="_blank" rel="noreferrer"
                style={{ color: '#3fb950', fontFamily: 'var(--font-mono)', wordBreak: 'break-all' }}>
                {resultUrl} ↗
              </a>
            </div>
          )}

          {status === 'FAILED' && (
            <div className="alert-error" style={{ marginTop: 16 }}>
              ❌ Deploy thất bại. Kiểm tra logs phía trên để xem lỗi.<br />
              Thường gặp: Repo không có Dockerfile, hoặc GHCR_TOKEN chưa cấu hình.
            </div>
          )}
        </div>
      )}
    </div>
  )
}
