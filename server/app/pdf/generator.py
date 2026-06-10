"""
PDF Generation for ForgeService (пафосная фича).
Generates professional-looking documents:
- Акт приёмки-передачи
- Заказ-наряд
- Акт выполненных работ / Счёт

Uses ReportLab for high quality output.
"""

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas
from reportlab.lib import colors
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from pathlib import Path
from datetime import datetime

OUTPUT_DIR = Path(__file__).parent.parent.parent / "data" / "pdfs"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# Fonts with full Cyrillic support (bundled DejaVuSans)
FONT_DIR = Path(__file__).parent / "fonts"
FONT_REGULAR = "DejaVu"
FONT_BOLD = "DejaVu-Bold"

# Register fonts once (DejaVu has excellent Cyrillic + Latin coverage)
try:
    pdfmetrics.registerFont(TTFont(FONT_REGULAR, str(FONT_DIR / "DejaVuSans.ttf")))
    pdfmetrics.registerFont(TTFont(FONT_BOLD, str(FONT_DIR / "DejaVuSans-Bold.ttf")))
except Exception:
    # Fallback (will produce tofu/black squares for Cyrillic if fonts missing)
    FONT_REGULAR = "Helvetica"
    FONT_BOLD = "Helvetica-Bold"


def generate_work_order_pdf(work_order_data: dict, output_name: str = None) -> str:
    """
    Generate a professional Work Order / Акт document.
    work_order_data should contain keys like:
      id, vehicle, client, status, labor_cost, parts_cost, total_cost, items, etc.
    """
    if output_name is None:
        output_name = f"work_order_{work_order_data.get('id', 'unknown')}.pdf"

    path = OUTPUT_DIR / output_name
    c = canvas.Canvas(str(path), pagesize=A4)
    width, height = A4

    # Header
    c.setFont(FONT_BOLD, 18)
    c.drawCentredString(width/2, height - 25*mm, "FORGE SERVICE")

    c.setFont(FONT_REGULAR, 10)
    c.drawCentredString(width/2, height - 32*mm, "Профессиональный автосервис • Акт выполненных работ / Заказ-наряд")

    # Order info
    y = height - 50*mm
    c.setFont(FONT_BOLD, 12)
    c.drawString(20*mm, y, f"Заказ-наряд № {work_order_data.get('id')}")
    c.setFont(FONT_REGULAR, 10)
    y -= 7*mm
    c.drawString(20*mm, y, f"Дата: {datetime.now().strftime('%d.%m.%Y %H:%M')}")
    y -= 7*mm
    c.drawString(20*mm, y, f"Статус: {work_order_data.get('status', '—')}")

    # Vehicle & Client
    y -= 12*mm
    c.setFont(FONT_BOLD, 11)
    c.drawString(20*mm, y, "Автомобиль:")
    c.setFont(FONT_REGULAR, 10)
    y -= 6*mm
    vehicle = work_order_data.get("vehicle", {})
    c.drawString(25*mm, y, f"{vehicle.get('make', '')} {vehicle.get('model', '')} {vehicle.get('year', '')} • {vehicle.get('license_plate', '')}")

    y -= 10*mm
    c.setFont(FONT_BOLD, 11)
    c.drawString(20*mm, y, "Клиент:")
    c.setFont(FONT_REGULAR, 10)
    y -= 6*mm
    client = work_order_data.get("client", {})
    c.drawString(25*mm, y, client.get("full_name", "—"))
    y -= 5*mm
    c.drawString(25*mm, y, client.get("phone", "—"))

    # Works
    y -= 12*mm
    c.setFont(FONT_BOLD, 11)
    c.drawString(20*mm, y, "Выполненные работы:")

    y -= 8*mm
    items = work_order_data.get("items", [])
    for item in items[:8]:  # limit
        c.setFont(FONT_REGULAR, 9)
        c.drawString(25*mm, y, f"• {item.get('description', '')}")
        c.drawRightString(width - 20*mm, y, f"{item.get('total_price', 0):.0f} ₽")
        y -= 5*mm

    # Totals
    y -= 8*mm
    c.setFont(FONT_BOLD, 11)
    c.drawString(20*mm, y, "Итого:")
    y -= 6*mm
    c.setFont(FONT_REGULAR, 10)
    c.drawString(25*mm, y, f"Работы: {work_order_data.get('labor_cost', 0):.0f} ₽")
    y -= 5*mm
    c.drawString(25*mm, y, f"Запчасти: {work_order_data.get('parts_cost', 0):.0f} ₽")
    y -= 5*mm
    c.setFont(FONT_BOLD, 11)
    c.drawString(25*mm, y, f"К оплате: {work_order_data.get('total_cost', 0):.0f} ₽")

    # Footer
    c.setFont(FONT_REGULAR, 8)
    c.drawCentredString(width/2, 15*mm, "Документ сформирован автоматически системой ForgeService • Все права защищены")

    c.save()
    return str(path)


def generate_report_pdf(report_data: dict, output_name: str = None) -> str:
    """
    Generate a period report PDF.
    report_data: {
        "period_days": 30,
        "revenue": 123456,
        "orders_count": 25,
        "avg_check": 4938,
        "low_stock": 4,
        "top_services": [("ТО-1", 14), ...],
        "recent_orders": [{"id": 123, "total": 5000}, ...]
    }
    """
    if output_name is None:
        output_name = f"report_{datetime.now().strftime('%Y%m%d')}.pdf"

    path = OUTPUT_DIR / output_name
    c = canvas.Canvas(str(path), pagesize=A4)
    width, height = A4

    # Header
    c.setFont(FONT_BOLD, 18)
    c.drawCentredString(width/2, height - 25*mm, "FORGE SERVICE")

    c.setFont(FONT_REGULAR, 10)
    c.drawCentredString(width/2, height - 32*mm, f"Отчет за последние {report_data.get('period_days', 30)} дней")

    y = height - 50*mm

    # Summary
    c.setFont(FONT_BOLD, 12)
    c.drawString(20*mm, y, "Сводка:")
    y -= 8*mm

    c.setFont(FONT_REGULAR, 10)
    c.drawString(25*mm, y, f"Выручка: {report_data.get('revenue', 0):.0f} ₽")
    y -= 6*mm
    c.drawString(25*mm, y, f"Заказов закрыто: {report_data.get('orders_count', 0)}")
    y -= 6*mm
    c.drawString(25*mm, y, f"Средний чек: {report_data.get('avg_check', 0):.0f} ₽")
    y -= 6*mm
    c.drawString(25*mm, y, f"Позиций с низким остатком: {report_data.get('low_stock', 0)}")

    # Top services
    y -= 12*mm
    c.setFont(FONT_BOLD, 12)
    c.drawString(20*mm, y, "Топ услуг:")
    y -= 8*mm

    c.setFont(FONT_REGULAR, 10)
    for service, count in report_data.get("top_services", [])[:5]:
        c.drawString(25*mm, y, f"• {service} — {count} заказов")
        y -= 6*mm

    # Recent orders
    y -= 8*mm
    c.setFont(FONT_BOLD, 12)
    c.drawString(20*mm, y, "Последние закрытые заказы:")
    y -= 8*mm

    c.setFont(FONT_REGULAR, 9)
    for order in report_data.get("recent_orders", [])[:5]:
        c.drawString(25*mm, y, f"ЗН-{order.get('id')} — {order.get('total', 0):.0f} ₽")
        y -= 5*mm

    # Footer
    c.setFont(FONT_REGULAR, 8)
    c.drawCentredString(width/2, 15*mm, "Документ сформирован автоматически системой ForgeService • Все права защищены")

    c.save()
    return str(path)
