# receipts

Receipt scanning and bookkeeping app for Mary.

Upload photos of paper receipts → Anthropic Vision reads them → review and correct → confirm to save the image to Google Drive and append a row to a Google Sheet.

**Live at:** `https://receipts.kamradtfamily.net`  
**Auth:** Google Workspace OIDC — @kamradtfamily.net accounts only

---

## Stack

| Layer | Tech |
|-------|------|
| Backend | Java 21, Spring Boot 3.4, Spring Security OAuth2 |
| Frontend | React 18, Vite (bundled into the Spring Boot jar) |
| Auth | Google Workspace OIDC (`hd=kamradtfamily.net` enforced server-side) |
| Extraction | Anthropic Messages API (vision + tool use) |
| Storage | Google Drive (images), Google Sheets (rows) |
| Deploy | Docker (multi-stage), GitHub Actions → ghcr.io, ArgoCD, K3s |

---

## Environment variables / secrets

All secrets go in a Kubernetes secret named `receipts-secret` in the `receipts` namespace. Never commit these values.

| Variable | Required | Description |
|----------|----------|-------------|
| `GOOGLE_CLIENT_ID` | yes | OAuth2 client ID from Google Cloud Console |
| `GOOGLE_CLIENT_SECRET` | yes | OAuth2 client secret |
| `ANTHROPIC_API_KEY` | yes | Anthropic API key — reuse the existing cluster key if one exists |
| `SHEET_ID` | yes | Google Spreadsheet ID (from the URL) |
| `DRIVE_FOLDER_ID` | yes | Google Drive folder ID where images are uploaded |
| `SHEET_TAB` | no | Sheet tab name (default: `Receipts`) |
| `ANTHROPIC_MODEL` | no | Anthropic model (default: `claude-sonnet-4-5`) |

Create the secret:
```bash
kubectl create secret generic receipts-secret -n receipts \
  --from-literal=GOOGLE_CLIENT_ID=... \
  --from-literal=GOOGLE_CLIENT_SECRET=... \
  --from-literal=ANTHROPIC_API_KEY=... \
  --from-literal=SHEET_ID=... \
  --from-literal=DRIVE_FOLDER_ID=...
```

---

## Google Cloud setup

### 1. Create an OAuth2 client

In **Google Cloud Console → APIs & Services → Credentials → Create credentials → OAuth client ID**:

- Application type: **Web application**
- Authorized redirect URIs:
  - `https://receipts.kamradtfamily.net/login/oauth2/code/google`
  - `http://localhost:8080/login/oauth2/code/google` (local dev)

### 2. OAuth consent screen

- User type: **Internal** (restricts sign-in to the `kamradtfamily.net` org automatically)
- Scopes to add: `openid`, `email`, `profile`, `spreadsheets`, `drive`

### 3. Enable APIs

In **APIs & Services → Library**, enable:
- Google Sheets API
- Google Drive API

### 4. Google Sheet

Create a sheet with a header row in the first tab (default tab name: `Receipts`):
```
Date | Items | Total | Receipt
```
The app appends below the header. `SHEET_ID` is the long ID in the spreadsheet URL.

### 5. Google Drive folder

Create a folder in Drive for receipt images. Share it if needed. Copy the folder ID from its URL.

---

## Local development

```bash
# Backend (needs .env or exported env vars — see above)
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export ANTHROPIC_API_KEY=...
export SHEET_ID=...
export DRIVE_FOLDER_ID=...

./mvnw spring-boot:run
# Backend runs at http://localhost:8080

# Frontend (separate terminal)
cd receipts-frontend
npm install
npm run dev
# Vite dev server at http://localhost:5173, proxies /api to :8080
```

The redirect URI `http://localhost:8080/login/oauth2/code/google` must be registered in the OAuth client (see above).

---

## Deployment

GitHub Actions builds a single Docker image containing the React build and the Spring Boot jar:

```
push to main
  → docker build (node:20 → maven:21 → eclipse-temurin:21-jre-alpine)
  → push ghcr.io/rkamradt/receipts:sha-<7hex>
  → patch rkamradt-helm-charts/receipts/version-values.yaml
  → ArgoCD auto-syncs → K3s → Cloudflare tunnel
```

After the first deploy, add the Cloudflare tunnel route in the Zero Trust dashboard:
- Subdomain: `receipts`
- Domain: `kamradtfamily.net`
- Service: `http://receipts.receipts:8080`

Copy `ghcr-secret` into the `receipts` namespace:
```bash
kubectl get secret ghcr-secret -n portfolio -o json | python3 -c "
import sys, json
s = json.load(sys.stdin)
del s['metadata']['creationTimestamp']
del s['metadata']['resourceVersion']
del s['metadata']['uid']
s['metadata']['namespace'] = 'receipts'
print(json.dumps(s))
" | kubectl apply -f -
```

Then create the `receipts-secret` (see above).

---

## HEIC images

HEIC is accepted at the file input but requires iOS/Android to convert before upload. When sharing from the Photos app on iPhone, choose **JPEG** in the share sheet format options. The app handles JPEG, PNG, and WebP natively with EXIF rotation correction.
