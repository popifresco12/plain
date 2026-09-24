"""Limitador de peticiones compartido (slowapi).

Vive aparte de main.py para que los routers (tokens, avisos...) puedan
limitar sus endpoints sin importar main (import circular).
"""
from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address)
