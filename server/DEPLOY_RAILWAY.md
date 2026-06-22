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

**Что такое Volume?**
На Railway (особенно на бесплатном тарифе) обычная файловая система временная. При перезапуске сервиса, деплое или "засыпании" все файлы (база SQLite + загруженные фото) стираются.
**Volume** — это как отдельный постоянный диск, который подключается к сервису и сохраняет данные навсегда.

### Как найти и создать Volume (по твоему скрину Settings):

Из того, что ты скинул — ты смотришь **Settings** сервиса (Source, Networking, Scale, Build, Deploy и т.д.).

В этом виде раздела **Volumes** может не быть сразу видно (иногда он появляется только после определённых действий или в другом месте интерфейса).

**Попробуй по порядку:**

1. На странице, которую ты показал, посмотри внимательно после блока **Scale** и перед **Build** — иногда Volumes там.
2. Если нет — в **левом меню** сервиса (или сверху) переключись на вкладку **Variables**.
3. В Variables пролистай вниз — там должен быть большой блок **Volumes** или кнопка **Add Volume**.
4. Альтернатива: на главной странице сервиса (нажми на название сервиса слева сверху, чтобы выйти из Settings) посмотри в правом верхнем углу большую кнопку **+ Add**. Нажми её и выбери **Volume**.

Создавай Volume с этими параметрами:
- **Name**: `data`
- **Mount Path**: `/data` (точно так, с ведущим слешем!)
- Size: оставь минимальную (1GB)

После создания:
- Вернись во вкладку **Variables**.
- Добавь (или проверь) эти две переменные:

```
DATABASE_URL=sqlite+aiosqlite:////data/forgeservice.db
PHOTOS_DIR=/data/photos
```

- Нажми **Deploy** в правом верхнем углу.

**Проверка Volume:**
В сервисе перейди в **Shell** (ищи вкладку Shell или кнопку в меню).
Выполни:
```bash
ls /data
ls /data/photos
```
Должны показаться папки (после запуска seed.py там будет база и фото).

Если и так не находишь — скинь скрин главной страницы сервиса (не Settings) или скажи, какие вкладки видишь слева. Подкорректируем.

1. Зайди в свой проект: https://railway.com/project/9b430567-d93b-465d-87a8-c2d43d35bc9a
2. Выбери нужный сервис (backend, не android-app).
3. В верхнем меню или слева найди вкладку **Settings** (иконка шестерёнки).
4. Пролистай страницу **вниз**.
5. Найди раздел **Volumes** (или "Persistent Storage").
6. Нажми **+ Add Volume** / **Create Volume**.
7. Заполни:
   - **Name**: `data`
   - **Mount Path**: `/data`  (обязательно именно так, со слешем)
   - Size: можно оставить по умолчанию (1 GB достаточно для начала)
8. Нажми Create.

После создания Volume **обязательно** перейди во вкладку **Variables** и добавь/обнови эти переменные:

```
DATABASE_URL=sqlite+aiosqlite:////data/forgeservice.db
PHOTOS_DIR=/data/photos
```

**Важно:** Не помечай их как Secret (убери замочек/маску), иначе может вылезти ошибка "secret ID missing" при сборке. Оставь их обычными переменными (значения видимы).

Затем нажми **Deploy** заново (или Redeploy latest).

**Как проверить, что Volume работает:**
- Зайди в сервис → вкладка **Shell**
- Выполни команды:
  ```
  ls /data
  ls /data/photos
  ```
  Должны появиться файлы после seed.

Если не видишь раздел Volumes — напиши, какой именно интерфейс у тебя открыт (можно описать или скинуть скрин), подскажу альтернативный путь.

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

## PostgreSQL на Railway (рекомендуется вместо SQLite)

PostgreSQL не требует Volume — данные сохраняются в облачной БД. Код уже поддерживает Postgres и MySQL.

### Шаг 1. Создать базу в Railway

1. Открой свой проект на https://railway.app
2. Нажми **+ New** (или **Add Service**) → **Database** → **PostgreSQL**
3. Дождись, пока сервис Postgres станет **Active**

### Шаг 2. Подключить БД к backend-сервису

**Способ A — через Reference (проще всего):**

1. Открой сервис **backend** (FastAPI), вкладка **Variables**
2. Нажми **+ New Variable** → **Add Reference**
3. Выбери сервис **PostgreSQL** → переменную **`DATABASE_URL`**
4. Railway сам подставит URL вида `postgresql://user:pass@host:port/railway`

**Способ B — вручную:**

1. Открой сервис **PostgreSQL** → вкладка **Connect**
2. Скопируй **Postgres Connection URL**
3. В сервисе backend → **Variables** добавь:
   ```
   DATABASE_URL=postgresql://user:password@host:port/railway
   ```

Код автоматически преобразует URL в `postgresql+asyncpg://...` для FastAPI.

### Шаг 3. Переменные backend-сервиса

Минимальный набор:

```
DATABASE_URL=<reference или скопированный URL из Postgres>
PHOTOS_DIR=/data/photos
RELOAD=false
```

- **Volume для фото** всё ещё нужен (фото хранятся на диске, не в БД)
- Переменную `DATABASE_URL` для SQLite (`sqlite+aiosqlite://...`) **удали**, если переходишь на Postgres

### Шаг 4. Деплой и seed

1. Нажми **Deploy** / **Redeploy**
2. После статуса **Active** открой **Shell** backend-сервиса
3. Запусти один раз:
   ```bash
   python seed.py
   ```
4. Проверь API: `https://твой-url.up.railway.app/health`

### Локальная разработка с Postgres

```bash
# В папке server/
pip install -r requirements.txt

# Windows PowerShell:
$env:DATABASE_URL="postgresql://user:pass@localhost:5432/forgeservice"
python seed.py
uvicorn app.main:app --reload
```

Без `DATABASE_URL` по умолчанию используется локальный SQLite.

### MySQL (альтернатива)

Railway также предлагает MySQL. Шаги те же, только:

1. Создай **MySQL** вместо PostgreSQL
2. Подключи `DATABASE_URL` через Reference
3. В `requirements.txt` раскомментируй `aiomysql` и `pymysql`, затем redeploy

Код преобразует `mysql://...` в `mysql+aiomysql://...` автоматически.

---

Good luck on defense! The public URL makes the demo much more impressive.