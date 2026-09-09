# Oracle Cloud Always Free — Manager API host

**UI host:** Vercel (Angular)  
**API host:** Oracle Always Free VM running `deploy/docker-compose.yml` (API + Postgres)

This is the recommended free always-on path for Keynest Manager API. Keep the SPA on Vercel; do **not** put the Spring API on Vercel.

## Target hostnames

| Role | Provider | Example value |
| --- | --- | --- |
| UI (production) | Vercel | `https://keynest-manager.vercel.app` |
| UI (local) | `ng serve` | `http://localhost:4200` |
| API (Oracle VM) | Public IP or DNS | `http://<ORACLE_PUBLIC_IP>:8080` |
| API base used by Angular | `environment.apiBaseUrl` / `API_BASE_URL` | `http://<ORACLE_PUBLIC_IP>:8080/api/v1` |

Until you attach a domain + TLS (optional below), the API is **HTTP on :8080**. That is acceptable for a private demo; use HTTPS before sharing credentials widely.

> **Do not** set `apiBaseUrl` to the Vercel UI origin (e.g. `https://keynest-manager.vercel.app/api/v1`). Vercel serves the SPA only — the API is on the Oracle VM.

## 1. Create the Always Free VM

1. Sign up at [Oracle Cloud](https://www.oracle.com/cloud/free/) (Always Free eligible home region).
2. Create a **VCN** with a **public subnet** (the wizard defaults are fine).
3. **Security list / NSG** ingress rules (source `0.0.0.0/0` for a public demo, tighten later):

   | Port | Purpose |
   | --- | --- |
   | 22/tcp | SSH |
   | 8080/tcp | Manager API (Compose publishes this) |
   | 80/443/tcp | Only if you add Caddy/nginx + a domain |

4. **Compute → Create instance**
   - Image: **Ubuntu 22.04** (or 24.04)
   - Shape: **VM.Standard.A1.Flex** (Ampere ARM) — Always Free eligible; e.g. 2 OCPU / 12 GB is plenty for API + Postgres  
     (If A1 capacity is exhausted in the AD, retry another AD or use an AMD Always Free micro shape.)
   - Assign a **public IP**
   - Add your SSH public key

5. Note the **public IP** — that is your API host until DNS exists.

## 2. Install Docker on the VM

SSH in:

```bash
ssh ubuntu@<ORACLE_PUBLIC_IP>
```

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker ubuntu
```

Log out and back in so the `docker` group applies.

## 3. Run Keynest API + Postgres

```bash
git clone https://github.com/JordyNicholas/Keynest.git
cd Keynest
cp deploy/oracle.env.example deploy/oracle.env
chmod 600 deploy/oracle.env
```

Edit `deploy/oracle.env`:

- Set a strong `KEYNEST_DB_PASSWORD`
- Set a **new random** `KEYNEST_VAULT_KEY_HEX` (64 hex chars) — do not keep the demo default on a public VM
- Set `KEYNEST_CORS_ALLOWED_ORIGINS` to your Vercel UI origin(s), comma-separated, e.g.  
  `https://keynest-manager.vercel.app,http://localhost:4200`

```bash
docker compose --env-file deploy/oracle.env -f deploy/docker-compose.yml up -d --build
docker compose -f deploy/docker-compose.yml ps
curl -sS "http://127.0.0.1:8080/api/v1/tools/health"
```

From your laptop:

```bash
curl -sS "http://<ORACLE_PUBLIC_IP>:8080/api/v1/tools/health"
```

Expect JSON with `"status":"ok"`.

### Updates later

```bash
cd ~/Keynest
git pull
docker compose --env-file deploy/oracle.env -f deploy/docker-compose.yml up -d --build
```

## 4. Point the Vercel UI at the Oracle API

After [#18](https://github.com/JordyNicholas/Keynest/issues/18) environments land (or already on your `KN#18` branch):

1. In `apps/manager-web/src/environments/prod.ts` set:

   ```ts
   apiBaseUrl: 'http://<ORACLE_PUBLIC_IP>:8080/api/v1',
   ```

2. Redeploy Vercel (production build).
3. Browser DevTools → Network: calls should go to the Oracle host, not to `*.vercel.app/api/...`.

CORS: the API must list your Vercel origin in `KEYNEST_CORS_ALLOWED_ORIGINS` (see above). Confirm with:

```bash
curl -i -X OPTIONS "http://<ORACLE_PUBLIC_IP>:8080/api/v1/tools/health" \
  -H "Origin: https://keynest-manager.vercel.app" \
  -H "Access-Control-Request-Method: GET"
```

Look for `Access-Control-Allow-Origin: https://keynest-manager.vercel.app`.

## 5. Optional — domain + HTTPS

When you have a DNS name (e.g. `api.yourdomain.com`):

1. A-record → Oracle public IP  
2. Put **Caddy** or **nginx** on the VM (or a tunnel) terminating TLS on 443 and proxying to `127.0.0.1:8080`  
3. Restrict security list so **8080 is no longer public** (only 443)  
4. Set `apiBaseUrl` to `https://api.yourdomain.com/api/v1`  
5. Keep CORS origins on HTTPS UI hosts only  

## Ops notes

- **Always Free** Ampere capacity can be unavailable in a region/AD — create the instance early; stop/start can lose the public IP unless you use a reserved IP.  
- Back up `KEYNEST_VAULT_KEY_HEX`; losing it makes existing envelopes unreadable.  
- Postgres data lives in the Compose volume `keynest_pg` — snapshot the block volume if the vault matters.  
- Free tier is for demos/MVP; monitor OCI “Always Free” resource counts so you do not accidentally create billable shapes.

## Checklist

- [ ] VM up with public IP; ports 22 + 8080 open  
- [ ] `docker compose … up` healthy; `/api/v1/tools/health` works remotely  
- [ ] Vault key + DB password are non-demo secrets  
- [ ] CORS includes the Vercel UI origin  
- [ ] Angular `prod` `apiBaseUrl` points at Oracle (`…:8080/api/v1`), **not** the Vercel hostname  
- [ ] Vercel redeployed; browser Network tab confirms API host  
