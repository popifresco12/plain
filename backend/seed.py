#!/usr/bin/env python3
"""
Seed script for PLAIN backend - inserts default plans for Barcelona and Villena.
Run: python seed.py
"""
import sys
sys.path.insert(0, '.')

from database import Base, SessionLocal, engine
from main import SEED_PLANS
from models import Plan

def main():
    print("🔄 Inicializando base de datos...")
    Base.metadata.create_all(bind=engine)
    
    db = SessionLocal()
    try:
        count = db.query(Plan).filter(Plan.is_default == True).count()
        if count == 0:
            print(f"🌱 Insertando {len(SEED_PLANS)} planes por defecto...")
            for p in SEED_PLANS:
                plan = Plan(**p, is_default=True)
                db.add(plan)
            db.commit()
            print(f"✅ Seeded {len(SEED_PLANS)} planes por defecto")
            
            # Show summary
            barcelona = sum(1 for p in SEED_PLANS if p['city'] == 'BARCELONA')
            villena = sum(1 for p in SEED_PLANS if p['city'] == 'VILLENA')
            print(f"   📍 Barcelona: {barcelona} planes")
            print(f"   📍 Villena: {villena} planes")
        else:
            print(f"📦 Ya hay {count} planes por defecto en la BD")
    finally:
        db.close()

if __name__ == "__main__":
    main()