# MiniPaaS — Nền tảng Tự động hoá Triển khai
### Dự án Học thuật — Điện toán Đám mây (AWS Cloud)

---

## 🏗️ Kiến trúc Hệ thống

```
[Người dùng] → http://<EC2-IP>:8080 (Dashboard)
                   ↓
         [Spring Boot Control Plane]
                   ↓ Fabric8 K8s Client (qua Tailscale 100.x.x.x)
         [K3s Master Node — AWS EC2 t2.micro]
           ├── Traefik Ingress → *.nip.io  
           ├── AWS RDS (PostgreSQL)  
           └── GitHub Actions (Remote Build)
```

### Các khái niệm Cloud Computing được minh họa

| Khái niệm | Thành phần |
|-----------|-----------|
| Container Orchestration | K3s (Kubernetes lightweight) |
| Cloud Database | AWS RDS (Relational Database Service) |
| Mesh VPN / Overlay Network | Tailscale (WireGuard protocol) |
| CI/CD Offloading | GitHub Actions Dispatch API |
| Service Discovery & Ingress | Traefik (built-in K3s) |
| Infrastructure as Code | Shell scripts, K8s YAML manifests |
| Async Pipeline | Spring @Async + SSE streaming |
| Platform as a Service | Toàn bộ hệ thống |

---

## 💰 Chi phí — Thiết kế cho AWS Free Tier ($0/năm đầu)

| Dịch vụ | Giới hạn Free | Mục đích |
|---------|-------------|---------|
| AWS EC2 (t2/t3.micro) | 750 giờ/tháng (1 vCPU, 1GB RAM) | VM chạy K3s Control Plane |
| AWS RDS (db.t3.micro) | 750 giờ/tháng | Database |
| Tailscale | 100 thiết bị | Mesh VPN |
| GHCR & GitHub Actions | 2000 phút & Unlimited Public | Build & lưu trữ Image |
| nip.io | Unlimited | Wildcard DNS |

---

## 🗂️ Cấu trúc dự án

```
HMCDOP/
├── backend/                    # Spring Boot Control Plane
│   └── ...                     # Code Backend Java
├── frontend/                   # React Dashboard (4 trang)
│   └── ...                     # Code Frontend
├── infrastructure/
│   ├── aws/
│   │   └── install-ec2-master.sh # Setup K3s + 4GB Swap trên AWS EC2
│   └── postgres/init.sql
├── .github/workflows/
│   └── remote-build.yml        # Nhận lệnh build từ Spring Boot
└── docker-compose.yml          # Local dev (mock K8s)
```

---

## 🚀 Chạy Local (Mock Mode)

```bash
# Clone project
git clone <repository-url>
cd HMCDOP

# Optional: copy environment template (see .env.example)
cp .env.example .env

# Chạy services (PostgreSQL + Spring Boot + React)
docker-compose up -d

# Truy cập
# Dashboard: http://localhost:5173
# API:       http://localhost:8080/api/v1/health
```

> **K8S_MOCK=true** — K8s operations được giả lập. Bạn có thể test UI và luồng API.

---

## ☁️ Deploy lên AWS

Hệ thống được tối ưu đặc biệt để chạy mượt mà trên giới hạn RAM 1GB của AWS EC2 Free Tier bằng cách tự động gán Swap 4GB và offload việc build tới GitHub.

### Bước 1: Tạo VM và Database trên AWS
1. Đăng ký/Đăng nhập [AWS Console](https://aws.amazon.com/free/).
2. Tạo 1 **EC2 Instance (t2.micro)** Ubuntu 22.04. Mở ports trong Security Group: **22, 80, 443, 6443, 8472, 10250, 8080**.
3. Tạo **AWS RDS (db.t3.micro - Postgres)** để giải phóng RAM cho EC2. Ghi lại URL endpoint của DB.

### Bước 2: Khởi tạo EC2 Master Node (kèm 4GB Swap)
```bash
ssh ubuntu@<VM-public-ip>
export TS_AUTHKEY="tskey-auth-xxxxx" # Lấy từ tailscale.com
# Script tự động tạo 4GB Swap và cài đặt K3s siêu nhẹ
bash <(curl -fsSL https://raw.githubusercontent.com/your-repo/main/infrastructure/aws/install-ec2-master.sh)
```

### Bước 3: Cấu hình Spring Boot dùng AWS RDS và GitHub Actions
```bash
# Tạo file môi trường
cat > /home/ubuntu/minipaas.env << EOF
# Dùng Endpoint của AWS RDS cấp
DB_HOST=minipaas-db.xxxx.us-east-1.rds.amazonaws.com
DB_USER=postgres
DB_PASSWORD=your_rds_password
DB_NAME=minipaas

# Các biến K8s / GitHub
K8S_MOCK=false
KUBECONFIG=/home/ubuntu/.kube/config
GHCR_USER=your-github-username
GHCR_TOKEN=ghp_xxxx
MASTER_IP=$(curl -s ifconfig.me)
JWT_SECRET=$(openssl rand -base64 32)

# Khởi động chế độ GitHub Actions Build (thay cho Kaniko K8s nội bộ)
BUILD_STRATEGY=github
EOF

# Build & Run Spring Boot
cd backend
mvn package -DskipTests
source /home/ubuntu/minipaas.env
java -jar target/minipaas-backend-*.jar
```

---

## ⚙️ API Endpoints

| Method | Endpoint | Mô tả |
|--------|---------|-------|
| POST | `/api/v1/auth/register` | Đăng ký tài khoản |
| POST | `/api/v1/auth/login` | Đăng nhập → JWT |
| POST | `/api/v1/deployments` | Deploy từ GitHub URL |
| GET | `/api/v1/deployments` | Danh sách deployments |
| GET | `/api/v1/deployments/{id}/logs` | SSE build logs |
| DELETE | `/api/v1/deployments/{id}` | Stop app |
| GET | `/api/v1/nodes` | K3s cluster nodes |
| GET | `/api/v1/health` | Health check |

---

## 🔄 Luồng Deploy (Sử dụng GitHub Actions Build Proxy)

```
POST /api/v1/deployments { githubUrl, branch }
  │
  ├─ Lưu DB: status=PENDING
  ├─ Trả về: { id, status: "PENDING" }
  │
  └─ @Async thread:
       │
       ├─ Kiểm tra nhánh BUILD_STRATEGY=github
       │
       ├─ BƯỚC 1: REST POST đến GitHub API -> Triggers '.github/workflows/remote-build.yml'
       ├─ BƯỚC 2: GitHub Action tự động: Clone repo -> Build Image -> Đẩy lên GHCR
       ├─ BƯỚC 3: SSE Stream thông báo đang chờ build ("⏳ Đã gửi lệnh cho GitHub...")
       │
       ├─ BƯỚC 4: (Sau khi github đẩy log) K8s: createNamespace() & createSecret()
       ├─ BƯỚC 5: K8s: createDeployment(image: ghcr.io/...) trên K3s EC2
       ├─ BƯỚC 6: K8s: createService(ClusterIP)
       ├─ BƯỚC 7: K8s: createIngressRoute → app.EC2-IP.nip.io
       │
       └─ DB: status=RUNNING, url=http://...nip.io
              SSE event: { status: "RUNNING", url: "..." }
```
