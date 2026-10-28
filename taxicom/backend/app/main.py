import asyncio
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .api.auth import me_router, router as auth_router
from .api.devices import router as devices_router, ws_router
from .api.reports import offers_router, reports_router
from .api.telegram import router as telegram_router
from .db import Base, engine
from .scheduler import scheduler_loop


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(engine)
    task = None
    if os.getenv("TAXICOM_SCHEDULER", "1") == "1":
        task = asyncio.create_task(scheduler_loop())
    yield
    if task:
        task.cancel()


app = FastAPI(title="Taxi Commandos API", version="0.1.0", lifespan=lifespan)

for r in (auth_router, me_router, devices_router, ws_router, offers_router, reports_router, telegram_router):
    app.include_router(r)


@app.get("/healthz", tags=["ops"])
def healthz() -> dict:
    return {"status": "ok", "service": "taxicom-api"}
