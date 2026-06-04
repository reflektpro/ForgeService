# Deploy ForgeService to Railway (Free Tier)

Railway.app is currently one of the easiest free hosting platforms for FastAPI + SQLite.

## 1. Prepare your code

1. Push your project to GitHub (the whole `ForgeService` folder or at least the `server` folder).

## 2. Create project on Railway

1. Go to https://railway.app and sign up (GitHub login is easiest).
2. Click **New Project** → **Deploy from GitHub repo**.
3. Select your repo.
4. Railway will detect the Python project.

**Critical settings (this fixes most build errors):**
- In the service settings (after creating), go to **Settings** tab:
  - **Root Directory**: set to `server` (very important! so it finds `requirements.txt`, `Procfile`, `app/` package)
  - **Build Command**: leave empty (Railway auto-detects)
  - **Start Command**: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`

We also added `runtime.txt` and `nixpacks.toml` in the `server/` folder to help the build process (Python 3.11 + correct packages).

## Troubleshooting "Failed to build an image"

If you see the exact error you posted:
1. Click **View logs** or expand the "Build › Build image" step.
2. Look for the real error (e.g. "No module named ...", "requirements.txt not found", Python version issues).
3. Common fixes:
   - Double-check **Root Directory** is exactly `server` (not empty, not `ForgeService`).
   - Redeploy after changing settings (click "Deploy" or "Redeploy").
   - The `runtime.txt` and `nixpacks.toml` we pushed should help — make sure they are in the repo at `server/runtime.txt` and `server/nixpacks.toml`.
   - If still fails, try adding this environment variable in Railway:
     ```
     NIXPACKS_PYTHON_VERSION=3.11
     ```

If you paste the detailed build log here, I can tell you the exact fix.

## 3. Add Persistent Storage (Volume) — IMPORTANT for SQLite + Photos

Without a volume, your database and uploaded photos will be lost on every restart.

1. In your service → **Variables** tab → **Volumes** (or search "Volume").
2. Create a new Volume:
   - Name: `data`
   - Mount path: `/data`
3. This gives you persistent disk.

## 4. Environment Variables

Go to **Variables** tab and add:

```
DATABASE_URL=sqlite+aiosqlite:////data/forgeservice.db
PHOTOS_DIR=/data/photos
RELOAD=false
```

(You can also set `PORT` — Railway sets it automatically.)

## 5. First Deploy + Seed the database

1. Click **Deploy**.
2. After it becomes "Active", go to your service.
3. Open **Shell** (or use the "Run" command in deployment).
4. Run the seed script once:
   ```bash
   python seed.py
   ```

   This will create all the realistic data (clients, cars, orders, parts, bays, etc.).

Alternatively, you can run it locally first, copy the `forgeservice.db` to the volume (advanced).

## 6. Get your public URL

In your Railway service page (not variables), look for the **public domain** or **URL** (usually https://something.up.railway.app ). Copy it.

**Important for free tier:** The app sleeps after ~5 min inactivity. Open the URL in browser first to wake it up before testing the Android app.

## 7. Update Android App (CRITICAL - this is why "прога не работает")

1. In the repo, edit:
   `android-app/app/src/main/java/com/forgeservice/app/data/remote/ApiClient.kt`

2. Find the line:
   ```kotlin
   private var baseUrl: String = "https://YOUR-RAILWAY-URL.up.railway.app"
   ```
   Replace with your actual Railway public URL (https, no trailing / ).

3. In Android Studio:
   - Build → Clean Project
   - Build → Rebuild Project
   - Run on emulator or install APK on phone.

The app will now call your live backend.

Also run seed in Railway Shell (see step 5) so there is data.

## 8. Useful Railway tips

- The free plan has limits (apps sleep after inactivity). For defense/demo you can keep a tab open or use the "Always on" if you upgrade (or just wake it before showing).
- You can add a custom domain later.
- Logs are available in the dashboard — very useful.
- To re-seed or reset data: use the Shell and run `python seed.py` again.

## Alternative platforms

- **Fly.io** — better free volumes, slightly more setup.
- **Render.com** — very nice UI, but free plan has **no persistent disk** (SQLite will reset). Better if you switch to their free Postgres.
- **Oracle Cloud Always Free** — the most powerful "real" free tier (always-on VM), but requires more Linux/SSH knowledge.

## Switching to Postgres (more production-like)

If you want to use free Postgres (Railway or Render have it):

1. Add a Postgres database in Railway.
2. Copy the `DATABASE_URL` it gives you (it will be `postgresql://...`).
3. Add `asyncpg` to requirements.txt and install.
4. Change `DATABASE_URL` in variables.
5. The code already supports it (we use SQLAlchemy async).

Would you like me to prepare the Postgres-ready version?

---

Good luck on defense! The public URL makes the demo much more impressive.