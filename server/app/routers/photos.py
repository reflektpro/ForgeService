"""
Photos & Annotations router.
Handles upload, storage, and annotation of repair evidence photos.
"""

import os
import uuid
from datetime import datetime
from fastapi import APIRouter, Depends, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List

from app.db.database import get_db, PHOTOS_DIR
from app.models.models import Photo, PhotoAnnotation, WorkOrder
from app.schemas.schemas import (
    PhotoResponse, PhotoCreate, PhotoAnnotationCreate, PhotoAnnotationResponse
)
from app.websocket.manager import manager

router = APIRouter()


@router.post("/upload", response_model=PhotoResponse)
async def upload_photo(
    work_order_id: int = Form(...),
    category: str = Form("other"),
    description: str = Form(None),
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db),
):
    wo = await db.get(WorkOrder, work_order_id)
    if not wo:
        raise HTTPException(404, "Work order not found")

    # Create directory for this work order
    wo_dir = PHOTOS_DIR / str(work_order_id)
    wo_dir.mkdir(parents=True, exist_ok=True)

    # Unique filename
    ext = os.path.splitext(file.filename)[1] or ".jpg"
    filename = f"{uuid.uuid4().hex}{ext}"
    file_path = wo_dir / filename

    # Save file
    contents = await file.read()
    with open(file_path, "wb") as f:
        f.write(contents)

    # Relative path for serving
    relative_path = f"{work_order_id}/{filename}"

    photo = Photo(
        work_order_id=work_order_id,
        file_path=relative_path,
        category=category,
        description=description,
    )
    db.add(photo)
    await db.commit()
    await db.refresh(photo)

    # Real-time notification
    await manager.send_event(
        "work_order.photo_added",
        {"work_order_id": work_order_id, "photo_id": photo.id},
        work_order_id=work_order_id,
    )

    return photo


@router.get("/work-order/{work_order_id}", response_model=List[PhotoResponse])
async def list_photos_for_work_order(work_order_id: int, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        Photo.__table__.select().where(Photo.work_order_id == work_order_id).order_by(Photo.taken_at.desc())
    )
    # Using model directly
    photos = (await db.execute(
        Photo.__table__.select().where(Photo.work_order_id == work_order_id)
    )).all()
    # Simpler:
    from sqlalchemy import select
    res = await db.execute(select(Photo).where(Photo.work_order_id == work_order_id).order_by(Photo.taken_at.desc()))
    return res.scalars().all()


@router.post("/{photo_id}/annotations", response_model=PhotoAnnotationResponse, status_code=201)
async def add_annotation(
    photo_id: int, payload: PhotoAnnotationCreate, db: AsyncSession = Depends(get_db)
):
    photo = await db.get(Photo, photo_id)
    if not photo:
        raise HTTPException(404, "Photo not found")

    ann = PhotoAnnotation(photo_id=photo_id, **payload.model_dump())
    db.add(ann)
    await db.commit()
    await db.refresh(ann)

    await manager.send_event(
        "photo.annotation_added",
        {"photo_id": photo_id},
        work_order_id=photo.work_order_id,
    )
    return ann


@router.delete("/{photo_id}", status_code=204)
async def delete_photo(photo_id: int, db: AsyncSession = Depends(get_db)):
    photo = await db.get(Photo, photo_id)
    if not photo:
        raise HTTPException(404, "Photo not found")

    # delete file from disk
    full_path = PHOTOS_DIR / photo.file_path
    if full_path.exists():
        full_path.unlink()

    await db.delete(photo)
    await db.commit()

    await manager.send_event(
        "photo.deleted",
        {"photo_id": photo_id},
        work_order_id=photo.work_order_id,
    )
    return None
