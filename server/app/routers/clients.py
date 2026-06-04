"""
Clients router for ForgeService.
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List

from app.db.database import get_db
from app.models.models import Client
from app.schemas.schemas import ClientCreate, ClientUpdate, ClientResponse

router = APIRouter()


@router.get("/", response_model=List[ClientResponse])
async def list_clients(db: AsyncSession = Depends(get_db), q: str = "", limit: int = 50):
    stmt = select(Client).order_by(Client.full_name)
    if q:
        stmt = stmt.where(Client.full_name.ilike(f"%{q}%") | Client.phone.ilike(f"%{q}%"))
    result = await db.execute(stmt.limit(limit))
    return result.scalars().all()


@router.post("/", response_model=ClientResponse, status_code=201)
async def create_client(payload: ClientCreate, db: AsyncSession = Depends(get_db)):
    client = Client(**payload.model_dump())
    db.add(client)
    await db.commit()
    await db.refresh(client)
    return client


@router.get("/{client_id}", response_model=ClientResponse)
async def get_client(client_id: int, db: AsyncSession = Depends(get_db)):
    client = await db.get(Client, client_id)
    if not client:
        raise HTTPException(404, "Client not found")
    return client


@router.patch("/{client_id}", response_model=ClientResponse)
async def update_client(client_id: int, payload: ClientUpdate, db: AsyncSession = Depends(get_db)):
    client = await db.get(Client, client_id)
    if not client:
        raise HTTPException(404, "Client not found")

    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(client, field, value)

    await db.commit()
    await db.refresh(client)
    return client


@router.delete("/{client_id}", status_code=204)
async def delete_client(client_id: int, db: AsyncSession = Depends(get_db)):
    client = await db.get(Client, client_id)
    if client:
        await db.delete(client)
        await db.commit()
    return None