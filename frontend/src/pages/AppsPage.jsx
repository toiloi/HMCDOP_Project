import { useState, useEffect } from 'react'
import api from '../api/axios.js'

const STATUS_LABELS = {
  PENDING:  { label: 'Pending',  cls: 'badge-pending'  },
  BUILDING: { label: 'Building', cls: 'badge-building' },
  RUNNING:  { label: 'Running',  cls: 'badge-running'  },
  FAILED:   { label: 'Failed',   cls: 'badge-failed'   },
  STOPPED:  { label: 'Stopped',  cls: 'badge-stopped'  },
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

  // Auto-refresh mỗi 10s nếu có app đang BUILDING
  useEffect(() => {
    const hasBuilding = apps.some(a => a.status === 'BUILDING' || a.status === 'PENDING')
    if (!hasBuilding) return
    const timer = setInterval(loadApps, 10000)
    return () => clearInterval(timer)
  }, [apps])

  if (loading) return (
    <div className="page-header">
      <div className="flex gap-2" style={{ alignItems: 'center' }}>
        <span className="spinner" /> Đang tải danh sách ứng dụng...
      </div>
    </div>
  )

  return (
    <div>
      <div className="flex-between page-header">
        <div>
          <h1 className="page-title">📦 Ứng dụng đang chạy</h1>
          <p className="page-subtitle">Danh sách tất cả deployments của bạn</p>
        </div>
        <button className="btn" style={{ border: '1px solid var(--border)', background: 'transparent' }}
          onClick={loadApps}>
          🔄 Refresh
        </button>
      </div>

      {apps.length === 0 ? (
        <div className="card empty-state">
          <div className="icon">📭</div>
          <p>Chưa có ứng dụng nào được deploy</p>
          <p className="text-muted" style={{ marginTop: 8 }}>
            Chuyển đến tab "Deploy Mới" để bắt đầu
          </p>
        </div>
      ) : (
        <div className="card" style={{ padding: 0 }}>
          <table className="table">
            <thead>
              <tr>
                <th>Ứng dụng</th>
                <th>Repository</th>
                <th>Trạng thái</th>
                <th>URL Public</th>
                <th>Thời gian</th>
                <th>Hành động</th>
              </tr>
            </thead>
            <tbody>
              {apps.map(app => {
                const st = STATUS_LABELS[app.status] || STATUS_LABELS.STOPPED
                return (
                  <tr key={app.id}>
                    <td>
                      <div style={{ fontWeight: 500, fontSize: 13 }}>{app.appName}</div>
                      <div className="text-muted mono">{app.branch}</div>
                    </td>
                    <td>
                      <a href={app.githubUrl} target="_blank" rel="noreferrer" className="table-link">
                        {app.githubUrl.replace('https://github.com/', '')} ↗
                      </a>
                    </td>
                    <td><span className={`badge ${st.cls}`}>{st.label}</span></td>
                    <td>
                      {app.url
                        ? <a href={app.url} target="_blank" rel="noreferrer" className="table-link mono">
                            {app.url.replace('http://', '')} ↗
                          </a>
                        : <span className="text-muted">—</span>}
                    </td>
                    <td className="text-muted">
                      {new Date(app.createdAt).toLocaleString('vi-VN')}
                    </td>
                    <td>
                      {app.status !== 'STOPPED' && (
                        <button className="btn btn-danger btn-sm" onClick={() => stopApp(app.id)}>
                          ⏹ Stop
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
