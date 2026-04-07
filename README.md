# MiniPaaS — Nền tảng Tự động hoá Triển khai
### Dự án Học thuật — Điện toán Đám mây (Hybrid Multi-Cloud)

---

## 🏗️ Kiến trúc Hệ thống

```
[Người dùng] → http://<VM-IP>:8080 (Dashboard)
                   ↓
         [Spring Boot Control Plane]
                   ↓ Fabric8 K8s Client (qua Tailscale 100.x.x.x)
         [K3s Master Node — Oracle Cloud ARM]
           ├── Traefik Ingress → *.nip.io  
           ├── Worker 1 (Oracle Free ARM)
           └── Worker 2 (Oracle Free ARM)
```

### Các khái niệm Cloud Computing được minh họa

| Khái niệm | Thành phần |
|-----------|-----------|
| Container Orchestration | K3s (Kubernetes lightweight) |
| Mesh VPN / Overlay Network | Tailscale (WireGuard protocol) |
| Service Discovery & Ingress | Traefik (built-in K3s) |
| Rootless Container Build | Kaniko (thay Docker-in-Docker) |
| Infrastructure as Code | Shell scripts, K8s YAML manifests |
| Multi-tenancy Isolation | K8s Namespace per deployment |
| Async Pipeline | Spring @Async + SSE streaming |
| Platform as a Service | Toàn bộ hệ thống |

---

## 💰 Chi phí — $0 hoàn toàn

| Dịch vụ | Giới hạn Free | Mục đích |
|---------|-------------|---------|
| Oracle Cloud Always Free | 4 OCPU + 24GB RAM ARM (vĩnh viễn) | VMs chạy K3s |
| Tailscale | 100 thiết bị | Mesh VPN |
| GHCR (GitHub) | Unlimited public | Container registry |
| nip.io | Unlimited | Wildcard DNS |

---

## 🗂️ Cấu trúc dự án

```
HMCDOP/
├── backend/                    # Spring Boot Control Plane
│   ├── src/main/java/com/minipaas/
│   │   ├── controller/         # REST API + SSE
│   │   ├── service/            # DeploymentService, KubernetesService, BuildService
│   │   ├── model/              # JPA Entities
│   │   ├── repository/         # Spring Data JPA
│   │   ├── config/             # K8s, Async, Security
│   │   └── security/           # JWT Filter
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                   # React Dashboard (3 trang)
│   └── src/pages/
│       ├── LoginPage.jsx
│       ├── DeployPage.jsx      # SSE log viewer
│       ├── AppsPage.jsx
│       └── NodesPage.jsx
├── infrastructure/
│   ├── k3s/
│   │   ├── install-master.sh   # Setup K3s Master + Tailscale
│   │   ├── install-worker.sh   # Setup K3s Worker + Tailscale
│   │   └── rbac.yaml           # Quyền cho Spring Boot
│   └── postgres/init.sql
└── docker-compose.yml          # Local dev (mock K8s)
```

---

## 🚀 Chạy Local (Mock Mode)

```bash
# Clone project
cd c:\TOAINA\PROJECTS\HMCDOP

# Chạy services (PostgreSQL + Spring Boot + React)
docker-compose up -d

# Truy cập
# Dashboard: http://localhost:5173
# API:       http://localhost:8080/api/v1/health
```

> **K8S_MOCK=true** — K8s operations được giả lập. Build logs được mock để demo.

---

## 🌐 Deploy lên Oracle Cloud

### Bước 1: Tạo 3 VM trên Oracle Cloud Always Free

1. Đăng ký [Oracle Cloud](https://cloud.oracle.com/free)
2. Tạo 3 VM Instance:
   - Shape: **VM.Standard.A1.Flex** (ARM64)
   - Phân bổ: VM1 = 2 OCPU/12GB, VM2 = 1 OCPU/6GB, VM3 = 1 OCPU/6GB
   - OS: Ubuntu 22.04 LTS ARM
3. Mở ports trong Security List: **22, 80, 443, 6443, 8472, 10250, 8080**

### Bước 2: Lấy Tailscale Auth Key

1. Đăng nhập [tailscale.com](https://tailscale.com)
2. Vào **Settings → Keys → Generate auth key**
3. Chọn: Reusable + Ephemeral

### Bước 3: Setup VM 1 (K3s Master)

```bash
ssh ubuntu@<VM1-public-ip>
export TS_AUTHKEY="tskey-auth-xxxxx"
bash <(curl -fsSL https://raw.githubusercontent.com/your-repo/main/infrastructure/k3s/install-master.sh)
```

Ghi lại: `K3S_MASTER_TS_IP`, `K3S_TOKEN`, `MASTER_PUBLIC_IP`

### Bước 4: Setup VM 2, 3 (Workers)

```bash
ssh ubuntu@<VM2-public-ip>
export TS_AUTHKEY="tskey-auth-xxxxx"
export K3S_MASTER_TS_IP="100.x.x.x"
export K3S_TOKEN="K10xxxx..."
export WORKER_NAME="worker-01"
bash <(curl -fsSL https://raw.githubusercontent.com/your-repo/main/infrastructure/k3s/install-worker.sh)
```

### Bước 5: Configure Spring Boot trên VM 1

```bash
# Tạo GitHub PAT với quyền write:packages
# https://github.com/settings/tokens

# Tạo file .env
cat > /home/ubuntu/minipaas.env << EOF
DB_HOST=localhost
DB_USER=minipaas
DB_PASSWORD=your_password
K8S_MOCK=false
KUBECONFIG=/home/ubuntu/.kube/config
GHCR_USER=your-github-username
GHCR_TOKEN=ghp_xxxx
MASTER_IP=$(curl -s ifconfig.me)
JWT_SECRET=$(openssl rand -base64 32)
EOF

# Apply RBAC
kubectl apply -f infrastructure/k3s/rbac.yaml

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

## 🔄 Luồng Deploy Chi tiết

```
POST /api/v1/deployments { githubUrl, branch, port }
  │
  ├─ Lưu DB: status=PENDING
  ├─ Trả về: { id, status: "PENDING" }
  │
  └─ @Async thread:
       │
       ├─ 1. K8s: createNamespace("deploy-{id}")
       ├─ 2. K8s: createSecret("ghcr-credentials")
       ├─ 3. K8s: createJob("build-{ts}")
       │         initContainer: alpine/git → clone repo
       │         container: kaniko → build + push GHCR
       │
       ├─ 4. SSE stream: Kaniko pod logs → frontend
       │
       ├─ 5. K8s: createDeployment(image: ghcr.io/...)
       ├─ 6. K8s: createService(ClusterIP)
       ├─ 7. K8s: createIngressRoute → app.IP.nip.io
       │
       └─ 8. DB: status=RUNNING, url=http://...nip.io
              SSE event: { status: "RUNNING", url: "..." }
```
