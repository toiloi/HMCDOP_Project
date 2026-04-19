import { useState, useEffect } from 'react'
import api from '../api/axios.js'

const STATUS_MAP = {
  PENDING:  { label: 'Pending',  cls: 'badge-pending'  },
  BUILDING: { label: 'Building', cls: 'badge-building' },
  RUNNING:  { label: 'Running',  cls: 'badge-running'  },
  FAILED:   { label: 'Failed',   cls: 'badge-failed'   },
  STOPPED:  { label: 'Stopped',  cls: 'badge-stopped'  },
}

function IconExternalLink() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 12, height: 12 }}>
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
      <polyline points="15 3 21 3 21 9"/><line x1="10" x2="21" y1="14" y2="3"/>
    </svg>
  )
}
function IconRefresh() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 14, height: 14 }}>
      <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
      <path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
      <path d="M8 16H3v5"/>
    </svg>
  )
}
function IconStop() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 13, height: 13 }}>
      <rect width="14" height="14" x="5" y="5" rx="2"/>
    </svg>
  )
}

export default function AppsPage() {
  const [apps, setApps] = useState([])
  const [loading, setLoading] = useState(true)

  async function loadApps() {
    try {
      const { data } = await api.get('/deployments')
      setApps(data)
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  async function stopApp(id) {
    if (!confirm('Dừng và xóa ứng dụng này?')) return
    try {
      await api.delete(`/deployments/${id}`)
      setApps(prev => prev.map(a => a.id === id ? { ...a, status: 'STOPPED' } : a))
    } catch (e) {
      alert('Lỗi: ' + (e.response?.data?.error || e.message))
    }
  }

  useEffect(() => { loadApps() }, [])

  useEffect(() => {
    const hasBuilding = apps.some(a => a.status === 'BUILDING' || a.status === 'PENDING')
    if (!hasBuilding) return
    const timer = setInterval(loadApps, 10000)
    return () => clearInterval(timer)
  }, [apps])

  const runningCount = apps.filter(a => a.status === 'RUNNING').length
  const buildingCount = apps.filter(a => a.status === 'BUILDING' || a.status === 'PENDING').length
  const failedCount = apps.filter(a => a.status === 'FAILED').length

  if (loading) return (
    <div className="loading-page">
      <span className="spinner spinner-lg" />
      <span className="text-muted">Đang tải danh sách ứng dụng...</span>
    </div>
  )

  return (
    <div>
      {/* Header */}
      <div className="page-header">
        <div className="page-header-text">
          <h1 className="page-title">Applications</h1>
          <p className="page-subtitle">Danh sách tất cả deployments của bạn</p>
        </div>
        <button id="btn-refresh-apps" className="btn btn-secondary btn-sm" onClick={loadApps}>
          <IconRefresh /> Refresh
        </button>
      </div>

      {/* Stats row */}
      <div className="grid-3" style={{ marginBottom: 28 }}>
        <div className="stat-card">
          <div className="stat-label">Total</div>
          <div className="stat-value" style={{ color: 'var(--text)' }}>{apps.length}</div>
          <div className="stat-desc">Ứng dụng</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Running</div>
          <div className="stat-value" style={{ color: 'var(--success)' }}>{runningCount}</div>
          <div className="stat-desc">Đang chạy</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Building</div>
          <div className="stat-value" style={{ color: 'var(--building)' }}>{buildingCount}</div>
          <div className="stat-desc">Đang build</div>
        </div>
      </div>

      {/* Table or empty */}
      {apps.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-icon">📭</div>
            <div className="empty-title">Chưa có ứng dụng nào</div>
            <div className="empty-desc">Chuyển đến tab "Deploy" để bắt đầu triển khai ứng dụng đầu tiên</div>
          </div>
        </div>
      ) : (
        <div className="card" style={{ padding: 0 }}>
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th>Ứng dụng</th>
                  <th>Repository</th>
                  <th>Trạng thái</th>
                  <th>URL Public</th>
                  <th>Thời gian</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {apps.map(app => {
                  const st = STATUS_MAP[app.status] || STATUS_MAP.STOPPED
                  return (
                    <tr key={app.id}>
                      <td>
                        <div className="font-medium" style={{ fontSize: 13 }}>{app.appName}</div>
                        <div className="mono text-xs text-muted" style={{ marginTop: 2 }}>
                          {app.branch}
                        </div>
                      </td>
                      <td>
                        <a href={app.githubUrl} target="_blank" rel="noreferrer"
                          className="table-link flex-center gap-1">
                          {app.githubUrl.replace('https://github.com/', '')}
                          <IconExternalLink />
                        </a>
                      </td>
                      <td>
                        <span className={`badge ${st.cls}`}>
                          <span className="badge-dot"
                            style={{
                              background: st.cls === 'badge-running' ? 'var(--success)'
                                : st.cls === 'badge-building' ? 'var(--building)'
                                : st.cls === 'badge-failed' ? 'var(--danger)'
                                : st.cls === 'badge-pending' ? 'var(--warning)'
                                : 'var(--text-muted)'
                            }} />
                          {st.label}
                        </span>
                      </td>
                      <td>
                        {app.url
                          ? <a href={app.url} target="_blank" rel="noreferrer"
                              className="table-link mono flex-center gap-1">
                              {app.url.replace('http://', '')}
                              <IconExternalLink />
                            </a>
                          : <span className="text-muted">—</span>}
                      </td>
                      <td className="text-muted text-sm">
                        {new Date(app.createdAt).toLocaleString('vi-VN')}
                      </td>
                      <td>
                        {app.status !== 'STOPPED' && (
                          <button
                            id={`btn-stop-${app.id}`}
                            className="btn btn-danger btn-sm"
                            onClick={() => stopApp(app.id)}>
                            <IconStop /> Stop
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {failedCount > 0 && (
        <div className="alert alert-warning mt-4">
          <span>⚠</span>
          <span>{failedCount} ứng dụng bị lỗi. Kiểm tra logs để biết thêm chi tiết.</span>
        </div>
      )}
    </div>
  )
}
