#!/bin/bash
# =============================================================
#  install-worker.sh — Cài đặt K3s Worker Node + Tailscale
# =============================================================
# Chạy trên VM 2, 3 (Oracle Cloud Always Free, Ubuntu 22.04 ARM)
#
# Cách dùng:
#   export TS_AUTHKEY="tskey-auth-xxxxx"
#   export K3S_MASTER_TS_IP="100.x.x.x"      # Tailscale IP của master
#   export K3S_TOKEN="K10xxxx..."             # Token từ master
#   export WORKER_NAME="worker-01"
#   bash install-worker.sh
# =============================================================

set -euo pipefail

echo "================================================"
echo "  MiniPaaS — K3s Worker Node Setup"
echo "  VM: Oracle Cloud Always Free (ARM64)"
echo "================================================"

# ── BƯỚC 1: Cài Tailscale ──────────────────────────────────
echo ""
echo "[1/3] Cài đặt Tailscale & join Mesh VPN..."
curl -fsSL https://tailscale.com/install.sh | sh

sudo tailscale up \
  --authkey="${TS_AUTHKEY}" \
  --hostname="${WORKER_NAME:-worker-$(hostname)}"

TS_IP=$(tailscale ip -4)
echo "[1/3] Worker Tailscale IP: $TS_IP"
echo "      Master  Tailscale IP: $K3S_MASTER_TS_IP"

# Kiểm tra kết nối đến master qua Tailscale
echo "[1/3] Kiểm tra kết nối đến master..."
if ! ping -c 3 "$K3S_MASTER_TS_IP" &>/dev/null; then
  echo "❌ Không ping được master tại $K3S_MASTER_TS_IP"
  echo "   Kiểm tra lại Tailscale trên cả 2 máy"
  exit 1
fi
echo "[1/3] Kết nối Tailscale mesh OK!"

# ── BƯỚC 2: Cài K3s Agent ──────────────────────────────────
echo ""
echo "[2/3] Cài đặt K3s Agent (Worker)..."
echo "      Kết nối đến master: https://$K3S_MASTER_TS_IP:6443"
echo "      Tailscale IP của worker: $TS_IP"

# Cài K3s agent mode
# K3S_URL: địa chỉ API server (dùng Tailscale IP — không cần IP public!)
# K3S_TOKEN: token từ master để xác thực
# --node-ip: IP của worker trong cluster (dùng Tailscale IP)
curl -sfL https://get.k3s.io | \
  K3S_URL="https://$K3S_MASTER_TS_IP:6443" \
  K3S_TOKEN="$K3S_TOKEN" \
  sh -s - \
  --node-ip="$TS_IP" \
  --node-name="${WORKER_NAME:-worker-$(hostname)}"

echo "[2/3] K3s agent khởi động!"

# ── BƯỚC 3: Verify ─────────────────────────────────────────
echo ""
echo "[3/3] Kiểm tra trạng thái..."
sleep 10

# Kiểm tra K3s agent đang chạy
if sudo systemctl is-active --quiet k3s-agent; then
  echo "[3/3] ✅ K3s agent đang chạy!"
else
  echo "[3/3] ⚠️ K3s agent chưa ready. Xem log: sudo journalctl -u k3s-agent -f"
fi

echo ""
echo "================================================"
echo "  ✅ Worker Node Setup Hoàn Chỉnh!"
echo "================================================"
echo ""
echo "  Worker Name:   ${WORKER_NAME:-worker-$(hostname)}"
echo "  Tailscale IP:  $TS_IP"
echo "  Master IP:     $K3S_MASTER_TS_IP (Tailscale mesh)"
echo ""
echo "  Kiểm tra trên Master:"
echo "  kubectl get nodes"
echo ""
echo "  Node này sẽ xuất hiện trong danh sách nodes"
echo "  sau vài giây."
echo "================================================"
