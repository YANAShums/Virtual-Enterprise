"""One lifecycle for legacy and current routes, with cross-process MySQL locking."""
from contextlib import contextmanager
from threading import Lock
from sqlalchemy import text,select
from .models import TrainingRun,ModelVersion
from .ml_core import TrainingError

_local=Lock()


class LifecycleBusy(RuntimeError):
    pass


@contextmanager
def operation(session):
    if not _local.acquire(blocking=False):
        raise LifecycleBusy('MODEL_OPERATION_ALREADY_RUNNING')
    connection=None;acquired=False
    try:
        if session.bind.dialect.name=='mysql':
            # Dedicated connection: session.commit() must not return the lock owner to the pool.
            connection=session.bind.connect()
            acquired=connection.execute(text("SELECT GET_LOCK('flowstaff-ml-lifecycle',0)")).scalar()==1
            if not acquired:raise LifecycleBusy('MODEL_OPERATION_ALREADY_RUNNING')
        yield
    finally:
        if connection is not None:
            try:
                if acquired:connection.execute(text("SELECT RELEASE_LOCK('flowstaff-ml-lifecycle')"))
            finally:connection.close()
        _local.release()


def train_candidate(session,settings,request_key=None):
    from . import services
    with operation(session):
        if request_key:
            prior=session.scalars(select(TrainingRun).where(TrainingRun.request_key==request_key)).first()
            if prior:
                if prior.status=='SUCCESS':return session.get(ModelVersion,prior.version)
                raise LifecycleBusy('IDEMPOTENCY_KEY_REUSED')
        if session.bind.dialect.name=='mysql':
            from .project_bridge import sync_legacy_imports
            sync_legacy_imports(session,settings.today())
        return services.train_model(session,settings,request_key=request_key)


def activate(session,version,settings):
    from .services import activate_model
    with operation(session):
        return activate_model(session,version,settings)
