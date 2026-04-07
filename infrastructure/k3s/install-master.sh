#!/bin/bash
# =============================================================
#  install-master.sh — Cài đặt K3s Master Node + Tailscale
# =============================================================
# Chạy trên VM 1 (Oracle Cloud Always Free, Ubuntu 22.04 ARM)
#
# Cách dùng:
#   export TS_AUTHKEY="tskey-auth-xxxxx"
#   bash install-master.sh
#
# Sau khi chạy xong, lấy token để join worker:
#   sudo cat /var/lib/rancher/k3s/server/node-token
# =============================================================

set -euo pipefail

echo "================================================"
echo "  MiniPaaS — K3s Master Node Setup"
echo "  VM: Oracle Cloud Always Free (ARM64)"
echo "================================================"

# ── BƯỚC 1: Cài Tailscale ──────────────────────────────────
echo ""
echo "[1/5] Cài đặt Tailscale..."
curl -fsSL https://tailscale.com/install.sh | sh

echo "[1/5] Kết nối vào Tailscale mesh VPN..."
sudo tailscale up --authkey="${TS_AUTHKEY}" --hostname="k3s-master"

# Lấy địa chỉ IP Tailscale (100.x.x.x)
TS_IP=$(tailscale ip -4)
echo "[1/5] Tailscale IP: $TS_IP"

# ── BƯỚC 2: Cài K3s Master ─────────────────────────────────
echo ""
echo "[2/5] Cài đặt K3s (Kubernetes lightweight)..."
echo "      Bind trên Tailscale IP: $TS_IP"

# Cài K3s server mode
# --node-ip: bind K3s trên Tailscale IP (mesh VPN)
# --tls-san: thêm Tailscale IP vào TLS certificate (để worker kết nối)
# --disable traefik: dùng Traefik mặc định của K3s (đã có sẵn)
curl -sfL https://get.k3s.io | sh -s - \
  --node-ip="$TS_IP" \
  --advertise-address="$TS_IP" \
  --tls-san="$TS_IP"

echo "[2/5] K3s master khởi động thành công!"

# ── BƯỚC 3: Cấu hình kubectl ───────────────────────────────
echo ""
echo "[3/5] Cấu hình kubectl access..."
mkdir -p $HOME/.kube
sudo cp /etc/rancher/k3s/k3s.yaml $HOME/.kube/config
sudo chown $USER:$USER $HOME/.kube/config

# Thay đổi server URL thành Tailscale IP (thay vì 127.0.0.1)
sed -i "s|server: https://127.0.0.1:6443|server: https://$TS_IP:6443|g" $HOME/.kube/config
export KUBECONFIG=$HOME/.kube/config

echo "[3/5] kubectl config đã xong!"
kubectl get nodes

# ── BƯỚC 4: Cài Java 17 + Maven ────────────────────────────
echo ""
echo "[4/5] Cài đặt Java 17 (cho Spring Boot)..."
sudo apt-get update -qq
sudo apt-get install -y openjdk-17-jdk maven

java -version
echo "[4/5] Java 17 cài xong!"

# ── BƯỚC 5: Cấu hình Traefik cho wildcard nip.io ──────────
echo ""
echo "[5/5] Cấu hình Traefik Ingress..."

# Tạo HelmChartConfig để cấu hình Traefik
sudo tee /var/lib/rancher/k3s/server/manifests/traefik-config.yaml > /dev/null << 'EOF'
apiVersion: helm.cattle.io/v1
kind: HelmChartConfig
metadata:
  name: traefik
  namespace: kube-system
spec:
  valuesContent: |-
    # Cho phép Traefik expose HTTP trên port 80
    ports:
      web:
        exposedPort: 80
    # Log level
    logs:
      general:
        level: ERROR
EOF

echo "[5/5] Traefik đã cấu hình!"

# ── THÔNG TIN QUAN TRỌNG ───────────────────────────────────
NODE_TOKEN=$(sudo cat /var/lib/rancher/k3s/server/node-token)
PUBLIC_IP=$(curl -s ifconfig.me 2>/dev/null || echo "unknown")

echo ""
echo "================================================"
echo "  ✅ K3s Master Setup Hoàn Chỉnh!"
echo "================================================"
echo ""
echo "  Tailscale IP:  $TS_IP"
echo "  Public IP:     $PUBLIC_IP"
echo "  K3s API:       https://$TS_IP:6443"
echo ""
echo "  Kubeconfig:    $HOME/.kube/config"
echo "             (copy file này vào Spring Boot server)"
echo ""
echo "  Node Token (để join worker):"
echo "  $NODE_TOKEN"
echo ""
echo "  Để chạy Spring Boot Control Plane:"
echo "  export K3S_URL=https://$TS_IP:6443"
echo "  export KUBECONFIG=$HOME/.kube/config"
echo "  export K8S_MOCK=false"
echo "  export MASTER_IP=$PUBLIC_IP"
echo ""
echo "  URL pattern: http://app-{name}.$PUBLIC_IP.nip.io"
echo "================================================"
