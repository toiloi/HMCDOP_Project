import { useState, useEffect } from 'react'
import api from '../api/axios.js'

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

  if (loading) return <div className="page-header"><div className="flex gap-2" style={{ alignItems: 'center' }}>
    <span className="spinner" /> Đang tải thông tin cluster...
  </div></div>

  const readyCount = nodes.filter(n => n.status === 'Ready').length

  return (
    <div>
      <div className="flex-between page-header">
        <div>
          <h1 className="page-title">🖥️ K3s Cluster Nodes</h1>
          <p className="page-subtitle">
            Tất cả nodes được kết nối qua Tailscale Mesh VPN (100.x.x.x)
          </p>
        </div>
        <button className="btn" style={{ border: '1px solid var(--border)', background: 'transparent' }}
          onClick={load}>🔄 Refresh</button>
      </div>

      {/* Summary */}
      <div className="grid-2" style={{ marginBottom: 20, gridTemplateColumns: 'repeat(3, 1fr)' }}>
        <div className="card" style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--success)' }}>{nodes.length}</div>
          <div className="text-muted">Tổng Nodes</div>
        </div>
        <div className="card" style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--success)' }}>{readyCount}</div>
          <div className="text-muted">Ready</div>
        </div>
        <div className="card" style={{ textAlign: 'center' }}>
          <div style={{ fontSize: 28, fontWeight: 700, color: 'var(--warning)' }}>
            {nodes.length - readyCount}
          </div>
          <div className="text-muted">Not Ready</div>
        </div>
      </div>

      {/* Nodes Table */}
      <div className="card" style={{ padding: 0 }}>
        <table className="table">
          <thead>
            <tr>
              <th>Tên Node</th>
              <th>Role</th>
              <th>Trạng thái</th>
              <th>Tailscale IP</th>
              <th>CPU</th>
              <th>RAM</th>
            </tr>
          </thead>
          <tbody>
            {nodes.map(node => (
              <tr key={node.name}>
                <td style={{ fontWeight: 500 }}>{node.name}</td>
                <td>
                  <span className="badge" style={{ background: node.roles === 'master'
                    ? '#1a1a3a' : 'var(--bg)', border: '1px solid var(--border)',
                    color: node.roles === 'master' ? '#9ecaff' : 'var(--text-muted)' }}>
                    {node.roles}
                  </span>
                </td>
                <td>
                  <span className={`badge badge-${node.status === 'Ready' ? 'ready' : 'notready'}`}>
                    {node.status}
                  </span>
                </td>
                <td className="mono text-muted">{node.tailscaleIp || '—'}</td>
                <td>{node.cpuCores || '—'}</td>
                <td>{node.memoryMi || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card mt-8" style={{ background: 'var(--bg)' }}>
        <p className="text-muted" style={{ fontSize: 13 }}>
          <strong style={{ color: 'var(--text)' }}>💡 Kiến trúc Tailscale Mesh VPN:</strong><br />
          Tất cả nodes giao tiếp với nhau qua địa chỉ IP Tailscale (100.x.x.x) —
          dù nodes nằm ở các cloud provider khác nhau, chúng vẫn "nhìn thấy" nhau
          như trong cùng một mạng LAN. Lưu lượng được mã hóa bằng WireGuard.
        </p>
      </div>
    </div>
  )
}
