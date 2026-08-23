#!/usr/bin/env python3
"""
Seed script for PLAIN backend - inserts default plans for Barcelona and Villena.
Run: python seed.py
"""
import sys
import json
from datetime import date
sys.path.insert(0, '.')

from database import Base, SessionLocal, engine
from main import SEED_PLANS
from models import Plan

def parse_date(v):
    """Convierte '2026-09-04' (str) a date, o None."""
    if not v:
        return None
    try:
        return date.fromisoformat(str(v))
    except (ValueError, TypeError):
        return None

def main():
    print("🔄 Inicializando base de datos...")
    Base.metadata.create_all(bind=engine)
    
    db = SessionLocal()
    try:
        count = db.query(Plan).filter(Plan.is_default == True).count()
        if count == 0:
            print(f"🌱 Insertando {len(SEED_PLANS)} planes por defecto...")
            for p in SEED_PLANS:
                data = dict(p)
                if isinstance(data.get("tags"), list):
                    data["tags"] = json.dumps(data["tags"], ensure_ascii=False)
                # Parsear fechas
                data["available_from"] = parse_date(data.get("available_from"))
                data["available_until"] = parse_date(data.get("available_until"))
                plan = Plan(**data, is_default=True)
                db.add(plan)
            db.commit()
            print(f"✅ Seeded {len(SEED_PLANS)} planes por defecto")
            
            # Show summary
            cities = {}
            for p in SEED_PLANS:
                cities[p['city']] = cities.get(p['city'], 0) + 1
            for city, n in sorted(cities.items()):
                print(f"   📍 {city}: {n} planes")
        else:
            print(f"📦 Ya hay {count} planes por defecto en la BD")
    finally:
        db.close()

if __name__ == "__main__":
    main()