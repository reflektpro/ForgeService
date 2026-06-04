"""
ForgeService Seed Script (Sync version - very reliable)
Generates rich realistic demo data.

Run from the server/ folder:
    python seed.py
"""

import random
from datetime import datetime, timedelta, timezone
from pathlib import Path
import sys

# Make sure we can import the app package
server_dir = str(Path(__file__).parent.resolve())
if server_dir not in sys.path:
    sys.path.insert(0, server_dir)

from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from app.db.database import DATABASE_URL, Base
from app.models.models import (
    Client, Vehicle, Employee, Bay, WorkOrder, WorkOrderItem,
    Part, WorkOrderPartUsage, WorkOrderStatus
)

# Use sync engine for seed (much more reliable)
# Respect DATABASE_URL from env (for Railway volume etc.)
if "sqlite" in DATABASE_URL:
    sync_url = DATABASE_URL.replace("sqlite+aiosqlite", "sqlite")
else:
    sync_url = DATABASE_URL
engine = create_engine(sync_url, echo=False)

# Ensure foreign keys
from sqlalchemy import event
@event.listens_for(engine, "connect")
def set_fk(dbapi_conn, rec):
    dbapi_conn.execute("PRAGMA foreign_keys=ON")


MAKES_MODELS = [
    ("Lada", "Vesta"), ("Lada", "Granta"), ("Hyundai", "Solaris"),
    ("Kia", "Rio"), ("Toyota", "Camry"), ("Volkswagen", "Polo"),
    ("BMW", "3 Series"), ("Mercedes-Benz", "C-Class"), ("Skoda", "Octavia"),
]

NAMES = [
    "Иванов Иван Иванович", "Петров Сергей Александрович", "Смирнова Анна Викторовна",
    "Кузнецов Дмитрий Олегович", "Волкова Екатерина Сергеевна", "Морозов Андрей Николаевич",
]

MECHANIC_NAMES = ["Алексей Смирнов", "Дмитрий Ковалёв", "Роман Петров", "Игорь Волков"]

SERVICES = [
    ("Замена моторного масла и фильтра", 0.8, 2500),
    ("Диагностика ходовой части", 1.0, 1800),
    ("Замена передних тормозных колодок", 1.2, 3200),
    ("ТО-1 (регламентное)", 2.5, 8500),
    ("Замена свечей зажигания", 0.7, 2100),
    ("Диагностика двигателя (компьютерная)", 0.5, 1500),
]

PARTS_DATA = [
    ("Масло моторное 5W-40 4л", "MOTUL-5W40-4", 12, 2800, 4200),
    ("Масляный фильтр Hyundai/Kia", "HY-26300", 25, 450, 950),
    ("Тормозные колодки передние (комплект)", "BREMBO-PAD-334", 8, 1850, 3200),
    ("Свечи зажигания NGK (комплект 4шт)", "NGK-BKR6E", 15, 620, 1250),
    ("Тормозной диск (1 шт)", "ATE-240mm", 6, 2100, 3800),
]


def seed():
    print("[SEED] Creating tables if needed...")
    Base.metadata.create_all(engine)

    with Session(engine) as db:
        print("[SEED] Generating realistic demo data...")

        # Employees
        employees = []
        for name in MECHANIC_NAMES:
            emp = Employee(
                full_name=name,
                phone="+79" + str(random.randint(100000000, 999999999)),
                role="manager" if name == MECHANIC_NAMES[0] else "mechanic",
                hourly_rate=1350,
            )
            db.add(emp)
            employees.append(emp)

        # Bays
        bays = []
        for i in range(1, 5):
            bay = Bay(name=f"Бокс {i}", description="Подъёмник" if i % 2 == 0 else "")
            db.add(bay)
            bays.append(bay)

        # Clients + Vehicles
        clients = []
        vehicles = []
        for name in NAMES:
            client = Client(full_name=name, phone="+79" + str(random.randint(100000000, 999999999)))
            db.add(client)
            clients.append(client)

            for _ in range(random.randint(1, 2)):
                make, model = random.choice(MAKES_MODELS)
                v = Vehicle(
                    client=client,
                    make=make,
                    model=model,
                    year=random.randint(2016, 2024),
                    license_plate=f"А{random.randint(100,999)}ВС 77",
                    vin="XTA" + str(random.randint(100000000000, 999999999999)),
                    engine="1.6 MPI",
                    transmission=random.choice(["МКПП", "АКПП"]),
                    current_mileage=random.randint(48000, 172000),
                    color=random.choice(["Белый", "Чёрный", "Серый"]),
                )
                db.add(v)
                vehicles.append(v)

        db.commit()
        print(f"[SEED] {len(clients)} clients, {len(vehicles)} vehicles, {len(bays)} bays")

        # Parts
        parts = []
        for name, pn, qty, buy, sell in PARTS_DATA:
            p = Part(
                name=name, part_number=pn, quantity=qty,
                min_quantity=3, purchase_price=buy, sell_price=sell
            )
            db.add(p)
            parts.append(p)
        db.commit()
        print(f"[SEED] {len(parts)} parts in warehouse")

        # Work orders
        created = 0
        for _ in range(11):
            vehicle = random.choice(vehicles)
            status = random.choice(list(WorkOrderStatus))

            wo = WorkOrder(
                vehicle_id=vehicle.id,
                status=status,
                intake_mileage=max(10000, vehicle.current_mileage - random.randint(800, 6000)),
                intake_notes="Стук в подвеске" if random.random() > 0.7 else None,
                assigned_bay_id=random.choice(bays).id if random.random() > 0.4 else None,
            )
            db.add(wo)
            db.flush()

            # works
            for _ in range(random.randint(1, 3)):
                desc, hours, price = random.choice(SERVICES)
                item = WorkOrderItem(
                    work_order_id=wo.id,
                    description=desc,
                    hours=hours,
                    price_per_hour=price,
                    total_price=round(hours * price),
                )
                db.add(item)

            # parts usage
            if random.random() > 0.5:
                part = random.choice(parts)
                if part.quantity > 0:
                    qty = 1
                    usage = WorkOrderPartUsage(
                        work_order_id=wo.id,
                        part_id=part.id,
                        quantity=qty,
                        price_at_time=part.sell_price,
                    )
                    db.add(usage)
                    part.quantity -= qty

            wo.labor_cost = random.randint(4500, 12500)
            wo.parts_cost = random.randint(1200, 4800)
            wo.total_cost = wo.labor_cost + wo.parts_cost

            if status == WorkOrderStatus.COMPLETED:
                wo.completed_at = datetime.now(timezone.utc) - timedelta(days=random.randint(1, 18))

            created += 1

        db.commit()
        print(f"[SEED] {created} work orders created with mixed statuses")

        # One rich demo order (in progress)
        rich_v = vehicles[0]
        rich = WorkOrder(
            vehicle_id=rich_v.id,
            status=WorkOrderStatus.IN_PROGRESS,
            intake_mileage=rich_v.current_mileage - 800,
            intake_notes="Вибрация руля при торможении. Авто на подъёмнике.",
            assigned_bay_id=bays[0].id,
        )
        db.add(rich)
        db.flush()

        for desc, hours, price in SERVICES[:3]:
            db.add(WorkOrderItem(
                work_order_id=rich.id, description=desc,
                hours=hours, price_per_hour=price, total_price=round(hours*price)
            ))

        rich.labor_cost = 9800
        rich.parts_cost = 5400
        rich.total_cost = rich.labor_cost + rich.parts_cost

        db.commit()

        print("\n[SUCCESS] Seed completed!")
        print("You can now start the server and explore rich data.")


if __name__ == "__main__":
    seed()