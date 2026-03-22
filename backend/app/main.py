from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.routers import health, upload, calls


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield


app = FastAPI(title="Call Transcriber API", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.allowed_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(health.router, tags=["health"])
app.include_router(upload.router, tags=["upload"])
app.include_router(calls.router, tags=["calls"])
