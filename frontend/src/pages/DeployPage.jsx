import { useState, useEffect, useRef } from 'react'
import api from '../api/axios.js'

function IconGithub() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" style={{ width: 15, height: 15, flexShrink: 0 }}>
      <path d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"/>
    </svg>
  )
}

function IconExternalLink() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 13, height: 13 }}>
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
      <polyline points="15 3 21 3 21 9"/><line x1="10" x2="21" y1="14" y2="3"/>
    </svg>
  )
}

function IconArrowLeft() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 14, height: 14 }}>
      <path d="m12 19-7-7 7-7"/><path d="M19 12H5"/>
    </svg>
  )
}

export default function DeployPage() {
  const [githubUrl, setGithubUrl] = useState('')
  const [branch, setBranch] = useState('main')
  const [port, setPort] = useState(80)
  const [loading, setLoading] = useState(false)
  const [logs, setLogs] = useState([])
  const [status, setStatus] = useState(null) // null | 'BUILDING' | 'RUNNING' | 'FAILED'
  const [resultUrl, setResultUrl] = useState('')
  const logEndRef = useRef(null)

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [logs])

  function classifyLog(line) {
    if (line.includes('✅') || line.includes('thành công') || line.includes('SUCCESS')) return 'success'
    if (line.includes('❌') || line.includes('LỖI') || line.includes('ERROR') || line.includes('FAIL')) return 'error'
    if (line.includes('🔨') || line.includes('📦') || line.includes('INFO') || line.includes('>>>')) return 'info'
    return ''
  }

  async function handleDeploy(e) {
    e.preventDefault()
    setLogs([])
    setStatus('BUILDING')
    setResultUrl('')
    setLoading(true)

    try {
      const { data: dep } = await api.post('/deployments', { githubUrl, branch, port })
      const depId = dep.id

      addLog(`📋 Deployment ID: ${depId}`)
      addLog(`🔗 Repository: ${githubUrl}`)
      addLog(`🌿 Branch: ${branch}`)
      addLog('')

      const token = localStorage.getItem('token')
      const evtSource = new EventSource(
        `/api/v1/deployments/${depId}/logs?token=${encodeURIComponent(token ?? '')}`
      )
      let streamFinished = false

      evtSource.addEventListener('log', e => { addLog(e.data) })
      evtSource.addEventListener('status', e => {
        streamFinished = true
        const raw = e.data ?? ''
        const i = raw.indexOf(':')
        const statusStr = i === -1 ? raw : raw.slice(0, i)
        const url = i === -1 ? '' : raw.slice(i + 1)
        setStatus(statusStr)
        if (url) setResultUrl(url)
        evtSource.close()
        setLoading(false)
      })
      evtSource.onerror = () => {
        evtSource.close()
        setLoading(false)
        if (streamFinished) return
        setLogs(prev => [...prev, 'SSE error: lost log stream. Rebuild backend, login again, or: docker logs minipaas-backend'])
        setStatus('FAILED')
      }
    } catch (err) {
      const msg = err.response?.data?.error || err.message
      addLog(`❌ Lỗi: ${msg}`)
      setStatus('FAILED')
      setLoading(false)
    }
  }

  function addLog(line) { setLogs(prev => [...prev, line]) }
  function reset() { setLogs([]); setStatus(null); setResultUrl(''); setGithubUrl('') }

  return (
    <div>
      {/* Page header */}
      <div className="page-header">
        <div className="page-header-text">
          <h1 className="page-title">New Deployment</h1>
          <p className="page-subtitle">Deploy ứng dụng từ GitHub lên K3s cluster chỉ trong vài phút</p>
        </div>
      </div>

      {/* ── Deploy form ── */}
      {status === null && (
        <div style={{ maxWidth: 680 }}>
          <div className="card">
            <div className="card-title">
              <IconGithub /> Thông tin Repository
            </div>

            <form onSubmit={handleDeploy}>
              <div className="form-group">
                <label className="label label-required" htmlFor="input-github-url">GitHub URL</label>
                <input
                  id="input-github-url"
                  className="input"
                  type="url"
                  required
                  placeholder="https://github.com/username/my-app"
                  value={githubUrl}
                  onChange={e => setGithubUrl(e.target.value)}
                />
                <p className="input-hint">Repository phải là public và có Dockerfile ở thư mục gốc</p>
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label className="label" htmlFor="input-branch">Branch</label>
                  <input
                    id="input-branch"
                    className="input"
                    type="text"
                    placeholder="main"
                    value={branch}
                    onChange={e => setBranch(e.target.value)}
                  />
                </div>
                <div className="form-group">
                  <label className="label" htmlFor="input-port">Port ứng dụng</label>
                  <input
                    id="input-port"
                    className="input"
                    type="number"
                    min={1} max={65535}
                    value={port}
                    onChange={e => setPort(Number(e.target.value))}
                  />
                </div>
              </div>

              {/* Pipeline info */}
              <div className="pipeline">
                <div className="pipeline-step">
                  <div className="pipeline-step-icon">📥</div>
                  <span>Clone</span>
                </div>
                <div className="pipeline-arrow" />
                <div className="pipeline-step">
                  <div className="pipeline-step-icon">🔨</div>
                  <span>Kaniko Build</span>
                </div>
                <div className="pipeline-arrow" />
                <div className="pipeline-step">
                  <div className="pipeline-step-icon">📦</div>
                  <span>Push GHCR</span>
                </div>
                <div className="pipeline-arrow" />
                <div className="pipeline-step">
                  <div className="pipeline-step-icon">☸️</div>
                  <span>K3s Deploy</span>
                </div>
                <div className="pipeline-arrow" />
                <div className="pipeline-step">
                  <div className="pipeline-step-icon">🌐</div>
                  <span>Traefik URL</span>
                </div>
              </div>

              <div style={{ marginTop: 20 }}>
                <button
                  id="btn-deploy-submit"
                  type="submit"
                  className="btn btn-primary btn-full btn-lg"
                  disabled={loading}>
                  {loading
                    ? <><span className="spinner" /> Đang xử lý...</>
                    : '🚀 Bắt đầu Deploy'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Log viewer ── */}
      {status !== null && (
        <div style={{ maxWidth: 820 }}>
          {/* Header bar */}
          <div className="flex-between mb-4" style={{ flexWrap: 'wrap', gap: 12 }}>
            <div className="flex-center gap-3">
              <span className="font-semibold">Build Logs</span>
              {loading && (
                <span className="badge badge-building">
                  <span className="badge-dot" />
                  Building...
                </span>
              )}
              {status === 'RUNNING' && (
                <span className="badge badge-running">
                  <span className="badge-dot" />
                  Deployed
                </span>
              )}
              {status === 'FAILED' && (
                <span className="badge badge-failed">
                  <span className="badge-dot" style={{ background: 'var(--danger)' }} />
                  Failed
                </span>
              )}
            </div>
            <button id="btn-new-deploy" className="btn btn-secondary btn-sm" onClick={reset}>
              <IconArrowLeft /> New Deployment
            </button>
          </div>

          {/* Terminal */}
          <div className="log-container">
            <div className="log-header">
              <div className="log-header-left">
                <div className="log-dots">
                  <div className="log-dot red" />
                  <div className="log-dot yellow" />
                  <div className="log-dot green" />
                </div>
                <span className="log-title">deployment — build output</span>
              </div>
              {loading && <span className="spinner spinner-sm" />}
            </div>
            <div className="log-viewer">
              {logs.map((line, i) => (
                <div key={i} className={`log-line ${classifyLog(line)}`}>
                  {line || '\u00A0'}
                </div>
              ))}
              {loading && (
                <div className="log-line info">
                  ▌<span className="log-cursor" />
                </div>
              )}
              <div ref={logEndRef} />
            </div>
          </div>

          {/* Result URL */}
          {status === 'RUNNING' && resultUrl && (
            <div className="alert alert-success mt-4">
              <span>🎉</span>
              <div>
                <div className="font-semibold" style={{ marginBottom: 6 }}>Deploy thành công!</div>
                <a href={resultUrl} target="_blank" rel="noreferrer"
                  className="flex-center gap-1 mono text-sm"
                  style={{ color: 'var(--success)', wordBreak: 'break-all' }}>
                  {resultUrl} <IconExternalLink />
                </a>
              </div>
            </div>
          )}

          {status === 'FAILED' && (
            <div className="alert alert-error mt-4">
              <span>⚠</span>
              <div>
                <div className="font-semibold" style={{ marginBottom: 4 }}>Deploy thất bại</div>
                <div className="text-sm" style={{ opacity: 0.85 }}>
                  Xem logs ở trên để biết nguyên nhân. Thường gặp: Repo không có Dockerfile, hoặc GHCR_TOKEN chưa cấu hình.
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
