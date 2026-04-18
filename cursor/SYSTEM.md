# MiniPaaS — Single source of truth (cursor/)

Hybrid Mini-PaaS: React dashboard → Spring Boot control plane → K3s (Traefik, Kaniko/GitHub Actions) → GHCR. Optional Cloudflare DNS for real domains.

## Environment variables

| Variable | Purpose |
|----------|---------|
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | PostgreSQL (e.g. Neon) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Fallback JDBC pieces (local docker-compose) |
| `JWT_SECRET` | JWT signing |
| `KUBECONFIG`, `K8S_MOCK` | K3s client; mock skips real API calls |
| `GHCR_USER`, `GHCR_TOKEN` | Push/pull images to GHCR |
| `GITHUB_DISPATCH_REPO` | `owner/repo` for `repository_dispatch` workflow (when `APP_BUILD_STRATEGY=github`) |
| `APP_BUILD_STRATEGY` | `kaniko` (in-cluster) or `github` |
| `DEPLOYMENT_DOMAIN` | If set, Traefik host = `{app}.{domain}`; else `{app}.{IP}.nip.io` |
| `DEPLOYMENT_K3S_IP` | Public ingress IPv4 (nip.io + Cloudflare A record) |
| `DEPLOYMENT_URL_SCHEME` | `http` or `https` (URL shown to users) |
| `CLOUDFLARE_DNS_ENABLED`, `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ZONE_ID`, `CLOUDFLARE_PROXIED` | Create/update A record. **Proxied** only allowed for public IPs; Tailscale `100.x` / RFC1918 → API uses `proxied=false` (error 9003 otherwise). Public access still needs a routable ingress IP or Tunnel. |
| `MASTER_IP` | Legacy fallback for public IP if `DEPLOYMENT_K3S_IP` unset |
| `VITE_API_URL` | Documented base URL for browsers hitting backend directly |
| `VITE_PROXY_TARGET` | Vite `/api` proxy target; use `http://backend:8080` inside Docker Compose |

Local `.env` is loaded by `DotenvLoader` before Spring starts (repo root or `backend/../.env`). Docker Compose uses `env_file` with `required: false`.

## Deploy flow (API)

1. `POST /api/v1/deployments` — persists `PENDING`, starts async pipeline.
2. Namespace, GHCR secret, build (Kaniko job or GitHub dispatch), Deployment, Service, IngressRoute.
3. If Cloudflare enabled — `upsertARecord(hostname, DEPLOYMENT_K3S_IP)`.
4. Status `RUNNING` + public URL in DB; SSE log stream on `GET /api/v1/deployments/{id}/logs?token=<jwt>` (EventSource cannot send `Authorization`; `JwtAuthFilter` accepts `token` query param). `JwtAuthFilter` uses `@Lazy UserDetailsService` to avoid a Spring bean cycle with `SecurityConfig` / `UserService` / `PasswordEncoder`.

## Related files

- Backend config: `backend/src/main/resources/application.yml`
- Compose: `docker-compose.yml`, `.env.example`
- K8s client: `KubernetesService`, `BuildService`, `DeploymentService`
- DNS: `CloudflareDnsService`
