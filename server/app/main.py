"""
ForgeService - Professional Auto Service Management System
Main FastAPI application entrypoint.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from contextlib import asynccontextmanager
import os
import uvicorn

from app.db.database import init_db, PHOTOS_DIR
from app.routers import (
    clients,
    vehicles,
    work_orders,
    parts,
    employees,
    bays,
    photos,
    dashboard,
    appointments,
    pdf,
)
from app.websocket.manager import websocket_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    print("🚀 ForgeService starting up...")
    await init_db()
    print("✅ Database initialized")
    yield
    # Shutdown
    print("🛑 ForgeService shutting down...")


app = FastAPI(
    title="ForgeService API",
    description="Профессиональная система управления автосервисом.",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS — allow everything for local development (phones on Wi-Fi)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Serve photos statically (important for Android to download images)
app.mount("/photos", StaticFiles(directory=str(PHOTOS_DIR)), name="photos")

# Include routers
app.include_router(clients.router, prefix="/api/clients", tags=["Clients"])
app.include_router(vehicles.router, prefix="/api/vehicles", tags=["Vehicles"])
app.include_router(work_orders.router, prefix="/api/work-orders", tags=["Work Orders"])
app.include_router(parts.router, prefix="/api/parts", tags=["Parts / Warehouse"])
app.include_router(employees.router, prefix="/api/employees", tags=["Employees"])
app.include_router(bays.router, prefix="/api/bays", tags=["Service Bays"])
app.include_router(photos.router, prefix="/api/photos", tags=["Photos & Annotations"])
app.include_router(dashboard.router, prefix="/api/dashboard", tags=["Dashboard & Analytics"])
app.include_router(appointments.router, prefix="/api/appointments", tags=["Appointments"])
app.include_router(pdf.router, prefix="/api/pdf", tags=["Documents (PDF)"])

# WebSocket for real-time updates
app.include_router(websocket_router, prefix="/ws", tags=["WebSocket"])


@app.get("/", tags=["Health"])
async def root():
    return {
        "name": "ForgeService",
        "status": "running",
        "message": "Профессиональная система управления автосервисом. Сервер работает.",
        "docs": "/docs",
        "photos_base": "/photos",
    }


@app.get("/health", tags=["Health"])
async def health():
    return {"status": "healthy"}


if __name__ == "__main__":
    port = int(os.getenv("PORT", 8000))
    # reload=True only for local dev
    reload = os.getenv("RELOAD", "false").lower() == "true"
    uvicorn.run("app.main:app", host="0.0.0.0", port=port, reload=reload)