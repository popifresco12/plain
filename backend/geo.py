"""Geografía: índice de ciudades con coordenadas y cálculo de distancias.

Fuente de datos: GeoNames `cities15000` (https://download.geonames.org/export/dump/)
licencia **Creative Commons Attribution 4.0** — atribución requerida (ver README/DEPLOY).

El índice (`city_coords.json`) mapea nombre normalizado -> lista de variantes
[[lat, lng, cc], ...] ordenadas por población descendente, porque hay homónimos
(p. ej. VALENCIA existe en España y en Venezuela).
"""

import json
import math
import os
import unicodedata
from functools import lru_cache

_HERE = os.path.dirname(os.path.abspath(__file__))
_INDEX_PATH = os.path.join(_HERE, "city_coords.json")
_INDEX: dict | None = None
EARTH_R_KM = 6371.0


def norm(name: str) -> str:
    """Nombre de ciudad comparable: sin acentos, mayúsculas, espacios simples."""
    s = unicodedata.normalize("NFKD", name or "")
    s = "".join(ch for ch in s if not unicodedata.combining(ch))
    return " ".join(s.upper().split())


def _load() -> dict:
    global _INDEX
    if _INDEX is None:
        try:
            with open(_INDEX_PATH, encoding="utf-8") as fh:
                _INDEX = json.load(fh)
        except (OSError, json.JSONDecodeError):
            _INDEX = {}
    return _INDEX


def known_cities() -> int:
    return len(_load())


@lru_cache(maxsize=8192)
def resolve(city: str, prefer_cc: str | None = None) -> tuple[float, float, str] | None:
    """Coordenadas de una ciudad como (lat, lng, cc); None si no está en el índice.

    `prefer_cc` desempata homónimos (p. ej. Valencia ES frente a Valencia VE).
    """
    variants = _load().get(norm(city))
    if not variants:
        return None
    if prefer_cc:
        for lat, lng, cc in variants:
            if cc == prefer_cc:
                return (lat, lng, cc)
    lat, lng, cc = variants[0]      # ya ordenado por población descendente
    return (lat, lng, cc)


def haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Distancia en km entre dos puntos (esfera de radio terrestre medio)."""
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = p2 - p1
    dlam = math.radians(lng2 - lng1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlam / 2) ** 2
    return 2 * EARTH_R_KM * math.asin(math.sqrt(a))


@lru_cache(maxsize=512)
def country_hint(names: tuple[str, ...]) -> str | None:
    """País más frecuente entre las ciudades dadas (desempata homónimos).

    Se usa el país donde ya hay planes: si la app tiene planes en España y el
    usuario escribe "Valencia", casi seguro quiere la española, no la venezolana.
    """
    counter: dict[str, int] = {}
    for name in names:
        hit = resolve(name)
        if hit:
            counter[hit[2]] = counter.get(hit[2], 0) + 1
    if not counter:
        return None
    return max(counter.items(), key=lambda kv: kv[1])[0]


@lru_cache(maxsize=128)
def cities_in_radius(lat: float, lng: float, radius_km: int) -> dict:
    """Ciudades del índice dentro del radio: {NOMBRE_NORMALIZADO: (lat, lng, km)}.

    Para homónimos se toma la variante MÁS CERCANA (evita que un plan de la
    Valencia española se mida contra la venezolana). Cacheado: el barrido son
    ~34 000 comparaciones.
    """
    found: dict[str, tuple[float, float, float]] = {}
    for key, variants in _load().items():
        best: tuple[float, float, float] | None = None
        for vlat, vlng, _cc in variants:
            d = haversine_km(lat, lng, vlat, vlng)
            if best is None or d < best[2]:
                best = (vlat, vlng, d)
        if best and best[2] <= radius_km:
            found[key] = best
    return found
