#!/bin/bash

#export TS_AUTHKEY="tskey-auth-khóa-của-bạn"
#
#export K3S_MASTER_TS_IP="100.x.x.x"
#
# Lay khoa bao mat tren master
#sudo cat /var/lib/rancher/k3s/server/node-token
#export K3S_TOKEN="K109xxxx...."
#
#Create: nano install-worker.sh
#Run: bash install-worker.sh

set -euo pipefail

echo "================================================"
echo "  MiniPaaS — AWS EC2 Worker Node Setup"
echo "  VM Type: AWS t2.micro (1GB RAM Optimization)"
echo "================================================"

# ── BƯỚC 0: TỐI ƯU HÓA RAM (QUAN TRỌNG CHO AWS FREE TIER) ──
# Tạo 4GB Swap để ép máy 1GB RAM chạy được các tác vụ build image
echo "[0/3] Cấu hình Swap (Bộ nhớ ảo 4GB)..."
if [ ! -f /swapfile ]; then
    sudo fallocate -l 4G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    echo "✅ Đã kích hoạt 4GB Swap."
else
    echo "ℹ️ Swap đã tồn tại."
fi

# ── BƯỚC 1: Cài Tailscale ──────────────────────────────────
echo ""
echo "[1/3] Cài đặt Tailscale & join Mesh VPN..."
curl -fsSL https://tailscale.com/install.sh | sh

sudo tailscale up \
  --authkey="${TS_AUTHKEY}" \
  --hostname="${WORKER_NAME:-aws-worker}"

TS_IP=$(tailscale ip -4)
echo "[1/3] Worker Tailscale IP: $TS_IP"

# Kiểm tra kết nối đến master qua Tailscale
if ! ping -c 3 "$K3S_MASTER_TS_IP" &>/dev/null; then
  echo "❌ Không ping được master tại $K3S_MASTER_TS_IP"
  exit 1
fi
echo "[1/3] Kết nối Tailscale mesh OK!"

# ── BƯỚC 2: Cài K3s Agent ──────────────────────────────────
echo ""
echo "[2/3] Cài đặt K3s Agent (Worker)..."

# Thêm flag --kubelet-arg để quản lý tài nguyên tốt hơn trên máy yếu
curl -sfL https://get.k3s.io | \
  K3S_URL="https://$K3S_MASTER_TS_IP:6443" \
  K3S_TOKEN="$K3S_TOKEN" \
  sh -s - \
  --node-ip="$TS_IP" \
  --node-name="${WORKER_NAME:-aws-worker}" \
  --kubelet-arg="system-reserved=cpu=100m,memory=200Mi"

echo "[2/3] K3s agent khởi động!"

# ── BƯỚC 3: Verify ─────────────────────────────────────────
echo ""
echo "[3/3] Kiểm tra trạng thái..."
sleep 10

if sudo systemctl is-active --quiet k3s-agent; then
  echo "[3/3] ✅ K3s agent đang chạy!"
else
  echo "[3/3] ⚠️ K3s agent chưa ready."
fi