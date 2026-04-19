import { useState, useEffect } from 'react'
import api from '../api/axios.js'

function IconRefresh() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 14, height: 14 }}>
      <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
      <path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
      <path d="M8 16H3v5"/>
    </svg>
  )
}

export default function NodesPage() {
  const [nodes, setNodes] = useState([])
  const [loading, setLoading] = useState(true)

  async function load() {
    try {
      const { data } = await api.get('/nodes')
      setNodes(data)
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  if (loading) return (
    <div className="loading-page">
      <span className="spinner spinner-lg" />
      <span className="text-muted">Đang tải thông tin cluster...</span>
    </div>
  )

  const readyCount = nodes.filter(n => n.status === 'Ready').length
  const notReadyCount = nodes.length - readyCount

  return (
    <div>
      {/* Header */}
      <div className="page-header">
        <div className="page-header-text">
          <h1 className="page-title">K3s Cluster</h1>
          <p className="page-subtitle">
            Nodes được kết nối qua Tailscale Mesh VPN (100.x.x.x)
          </p>
        </div>
        <button id="btn-refresh-nodes" className="btn btn-secondary btn-sm" onClick={load}>
          <IconRefresh /> Refresh
        </button>
      </div>

      {/* Stats */}
      <div className="grid-3" style={{ marginBottom: 28 }}>
        <div className="stat-card">
          <div className="stat-label">Total Nodes</div>
          <div className="stat-value" style={{ color: 'var(--text)' }}>{nodes.length}</div>
          <div className="stat-desc">Trong cluster</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Ready</div>
          <div className="stat-value" style={{ color: 'var(--success)' }}>{readyCount}</div>
          <div className="stat-desc">Sẵn sàng</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Not Ready</div>
          <div className="stat-value" style={{ color: notReadyCount > 0 ? 'var(--danger)' : 'var(--text-muted)' }}>
            {notReadyCount}
          </div>
          <div className="stat-desc">Có vấn đề</div>
        </div>
      </div>

      {/* Node table */}
      <div className="card" style={{ padding: 0, marginBottom: 20 }}>
        <div className="table-wrapper">
          <table className="table">
            <thead>
              <tr>
                <th>Node Name</th>
                <th>Role</th>
                <th>Status</th>
                <th>Tailscale IP</th>
                <th>CPU</th>
                <th>RAM (Mi)</th>
              </tr>
            </thead>
            <tbody>
              {nodes.length === 0 ? (
                <tr>
                  <td colSpan={6}>
                    <div className="empty-state" style={{ padding: '40px 16px' }}>
                      <div className="empty-icon">🖥️</div>
                      <div className="empty-title">Không tìm thấy node nào</div>
                      <div className="empty-desc">Cluster có thể chưa được cấu hình</div>
                    </div>
                  </td>
                </tr>
              ) : nodes.map(node => (
                <tr key={node.name}>
                  <td>
                    <div className="font-medium mono" style={{ fontSize: 13 }}>{node.name}</div>
                  </td>
                  <td>
                    <span className={`badge ${node.roles === 'master' ? 'badge-master' : 'badge-stopped'}`}>
                      {node.roles}
                    </span>
                  </td>
                  <td>
                    <span className={`badge badge-${node.status === 'Ready' ? 'ready' : 'notready'}`}>
                      <span className="badge-dot"
                        style={{ background: node.status === 'Ready' ? 'var(--success)' : 'var(--danger)' }} />
                      {node.status}
                    </span>
                  </td>
                  <td className="mono text-muted text-sm">{node.tailscaleIp || '—'}</td>
                  <td className="text-secondary">{node.cpuCores ? `${node.cpuCores} cores` : '—'}</td>
                  <td className="text-secondary">{node.memoryMi ? `${node.memoryMi} Mi` : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Info card */}
      <div className="card" style={{ background: 'var(--bg-subtle)' }}>
        <div className="card-title">💡 Kiến trúc Tailscale Mesh VPN</div>
        <p className="text-secondary" style={{ fontSize: 13, lineHeight: 1.75 }}>
          Tất cả nodes giao tiếp với nhau qua địa chỉ IP Tailscale (<span className="mono">100.x.x.x</span>) —
          dù nodes nằm ở các cloud provider khác nhau (AWS, Oracle, GCP…), chúng vẫn "nhìn thấy" nhau
          như trong cùng một mạng LAN. Lưu lượng được mã hóa end-to-end bằng <strong style={{ color: 'var(--text)' }}>WireGuard</strong>.
        </p>
        <div className="flex-center gap-2 mt-4" style={{ flexWrap: 'wrap' }}>
          {['K3s Master', 'K3s Worker', 'Tailscale VPN', 'WireGuard Encryption', 'Traefik Ingress'].map(tag => (
            <span key={tag} style={{
              padding: '3px 10px',
              borderRadius: 20,
              background: 'var(--bg-elevated)',
              border: '1px solid var(--border)',
              fontSize: 11,
              color: 'var(--text-muted)'
            }}>{tag}</span>
          ))}
        </div>
      </div>
    </div>
  )
}
