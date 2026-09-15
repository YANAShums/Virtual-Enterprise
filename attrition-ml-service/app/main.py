"""Infrastructure skeleton; training and prediction belong to later stages."""
import os
from secrets import compare_digest
import pymysql
from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse

app = FastAPI(title="FlowStaff AI Attrition Service", version="0.1.0",
              docs_url=None, redoc_url=None, openapi_url=None)

def check_database():
    connection = pymysql.connect(
        host=os.getenv("ML_DB_HOST", "mysql"), port=int(os.getenv("ML_DB_PORT", "3306")),
        user=os.getenv("DB_USERNAME", "virtual_app"), password=os.environ["DB_PASSWORD"],
        database="enterprise_ml", connect_timeout=3, read_timeout=3)
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = 'ml-001-baseline'")
            if cursor.fetchone()[0] != 1:
                raise RuntimeError("ML migrations are not complete")
    finally:
        connection.close()

@app.get("/health")
def liveness():
    return {"status": "UP"}

@app.get("/internal/v1/health")
def readiness(x_service_token: str | None = Header(default=None)):
    expected = os.environ.get("ML_SERVICE_TOKEN", "")
    if not expected or not compare_digest((x_service_token or "").encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="INVALID_SERVICE_TOKEN")
    try:
        check_database()
    except Exception:
        return JSONResponse(status_code=503, content={"status": "DOWN", "database": "DOWN"})
    return {"status": "UP", "database": "UP", "model": "NOT_TRAINED"}
