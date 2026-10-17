from __future__ import annotations

import os

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from .models import Base


def make_session_factory(database_url: str) -> sessionmaker[Session]:
    if database_url.startswith("sqlite:///") and ":memory:" not in database_url:
        path = database_url.replace("sqlite:///", "", 1)
        if os.path.dirname(path):
            os.makedirs(os.path.dirname(path), exist_ok=True)
    kwargs = {"connect_args": {"check_same_thread": False}} if database_url.startswith("sqlite") else {}
    if ":memory:" in database_url:
        from sqlalchemy.pool import StaticPool
        kwargs["poolclass"] = StaticPool
    engine = create_engine(database_url, future=True, pool_pre_ping=True, **kwargs)
    # MySQL schemas are managed by Liquibase; imports must not mutate production DDL.
    if database_url.startswith("sqlite"):
        Base.metadata.create_all(engine)
    return sessionmaker(engine, expire_on_commit=False)
