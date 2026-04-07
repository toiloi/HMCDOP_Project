#!/bin/bash
# ==============================================================================
#  CÀI ĐẶT K3S MASTER NODE TRÊN AWS EC2 (T2.MICRO - FREE TIER)
# ==============================================================================
# Hỗ trợ Swap 4GB để tránh OOM Crash trên môi trường 1GB RAM.
#
# Yêu cầu trước khi chạy:
#   1. Instance có Elastic IP (Public IP cố định)
#   2. Đã lưu biến môi trường TS_AUTHKEY
#   3. Mở các Port: 22, 80, 443, 6443, 8472, 10250, 8080 trên Security Group AWS
#
# Cách chạy:
#   export TS_AUTHKEY="tskey-auth-xxxxx"
#   sudo -E bash install-ec2-master.sh
# ==============================================================================

set -e

echo "======================================================"
echo "🚀 BẮT ĐẦU CÀI ĐẶT AWS EC2 MASTER NODE (1GB RAM + SWAP)"
echo "======================================================"

if [ -z "$TS_AUTHKEY" ]; then
    echo "❌ LỖI: Chưa có biến môi trường TS_AUTHKEY."
    echo "Hãy lấy key tại: https://login.tailscale.com/admin/settings/keys"
    exit 1
fi

# 1. TẠO SWAP (VÔ CÙNG QUAN TRỌNG TRÊN AWS T2.MICRO)
echo "------------------------------------------------------"
echo "🧠 1. Khởi tạo 4GB Swap file để chữa nghẽn RAM"
echo "------------------------------------------------------"
if [ ! -f /swapfile ]; then
    sudo fallocate -l 4G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    # Giữ swap cố định mỗi khi reboot
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    
    # Tối ưu Swap swappiness xuống một mức hợp lí (khuyến cáo 10-60)
    sudo sysctl vm.swappiness=30
    echo 'vm.swappiness=30' | sudo tee -a /etc/sysctl.conf
    echo "✅ Tạo Swap 4GB thành công!"
else
    echo "✅ Swap file đã tồn tại!"
fi
free -h

# 2. CÀI ĐẶT TAILSCALE
echo "------------------------------------------------------"
echo "🌐 2. Cài đặt Tailscale Layer 3 Mesh VPN"
echo "------------------------------------------------------"
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up --authkey=${TS_AUTHKEY} --hostname=aws-master-node
TS_IP=$(tailscale ip -4)
echo "✅ Tailscale IP của node này: $TS_IP"


# 3. CÀI ĐẶT K3S MASTER
echo "------------------------------------------------------"
echo "📦 3. Cài đặt K3s Control Plane (Lightweight K8s)"
echo "------------------------------------------------------"
# Lấy Public IP của EC2 (lấy qua metadata của AWS)
PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 || curl -s ifconfig.me)

# Cài đặt K3s: 
# - Dùng flannel-backend=wireguard-native để traffic nội bộ của Cluster an toàn hơn qua Tailscale Ovelay.
# - Ràng buộc K3s listen trên IP của Tailscale thay vì mạng Public
curl -sfL https://get.k3s.io | sh -s - server \
  --node-ip $TS_IP \
  --advertise-address $TS_IP \
  --flannel-backend=wireguard-native \
  --flannel-external-ip \
  --tls-san $PUBLIC_IP \
  --tls-san $TS_IP \
  --disable servicelb \
  --kubelet-arg="system-reserved=memory=300Mi" \
  --kubelet-arg="kube-reserved=memory=300Mi" \
  --kubelet-arg="eviction-hard=memory.available<100Mi"

echo "⏳ Đợi K3s khởi động (10s)..."
sleep 10
sudo k3s kubectl get nodes

echo "------------------------------------------------------"
echo "🎉 HOÀN TẤT CÀI ĐẶT K3S MASTER NODE TRÊN AWS"
echo "------------------------------------------------------"
echo "Master Node Tailscale IP : $TS_IP"
echo "Public IP (TLS)          : $PUBLIC_IP"
echo "K3s Token (cho Worker)   : $(sudo cat /var/lib/rancher/k3s/server/node-token)"
echo ""
echo "Bạn có thể tải Kubeconfig về máy qua lệnh:"
echo "sudo cat /etc/rancher/k3s/k3s.yaml"
echo "======================================================"
