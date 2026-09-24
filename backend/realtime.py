"""Tiempo real: un WebSocket por usuario conectado.

Por el mismo canal viajan los mensajes de chat, el «está escribiendo» y los
avisos. Los endpoints normales son síncronos (se ejecutan en el threadpool de
FastAPI), así que publican con `hub.publish()`, que salta al event loop con
`run_coroutine_threadsafe`.

Limitación asumida: las conexiones viven en memoria del proceso. En Render hay
un solo proceso (WEB_CONCURRENCY=1), así que basta. Con varias réplicas habría
que poner Redis pub/sub delante; la app sigue funcionando igual porque además
consulta por HTTP (polling de respaldo).
"""
from __future__ import annotations

import asyncio
import json
import threading
from collections import defaultdict
from typing import Any

from fastapi import WebSocket


class Hub:
    def __init__(self) -> None:
        self._conns: dict[int, set[WebSocket]] = defaultdict(set)
        self._lock = threading.Lock()
        self.loop: asyncio.AbstractEventLoop | None = None

    def connect(self, user_id: int, ws: WebSocket) -> None:
        with self._lock:
            self._conns[user_id].add(ws)

    def disconnect(self, user_id: int, ws: WebSocket) -> None:
        with self._lock:
            conns = self._conns.get(user_id)
            if conns:
                conns.discard(ws)
                if not conns:
                    self._conns.pop(user_id, None)

    def is_online(self, user_id: int) -> bool:
        with self._lock:
            return bool(self._conns.get(user_id))

    def online_count(self) -> int:
        with self._lock:
            return sum(len(v) for v in self._conns.values())

    async def send_user(self, user_id: int, payload: dict[str, Any]) -> None:
        with self._lock:
            targets = list(self._conns.get(user_id, ()))
        if not targets:
            return
        text = json.dumps(payload, default=str, ensure_ascii=False)
        for ws in targets:
            try:
                await ws.send_text(text)
            except Exception:
                self.disconnect(user_id, ws)

    def publish(self, user_id: int, payload: dict[str, Any]) -> None:
        """Envía sin bloquear desde código síncrono. Si nadie está conectado, no hace nada."""
        loop = self.loop
        if loop is None or loop.is_closed() or not self.is_online(user_id):
            return
        try:
            running = asyncio.get_running_loop()
        except RuntimeError:
            running = None
        if running is loop:
            loop.create_task(self.send_user(user_id, payload))
        else:
            asyncio.run_coroutine_threadsafe(self.send_user(user_id, payload), loop)

    def publish_many(self, user_ids, payload: dict[str, Any]) -> None:
        for uid in set(user_ids):
            self.publish(uid, payload)


hub = Hub()
