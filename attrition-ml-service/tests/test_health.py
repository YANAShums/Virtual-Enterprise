from fastapi.testclient import TestClient
from app import main
client = TestClient(main.app)

def fail():
    raise RuntimeError("secret")

def test_liveness_is_independent(monkeypatch):
    monkeypatch.setattr(main, "check_database", fail)
    assert client.get("/health").json() == {"status": "UP"}

def test_readiness_requires_token(monkeypatch):
    monkeypatch.setenv("ML_SERVICE_TOKEN", "test-token")
    assert client.get("/internal/v1/health").status_code == 401
    assert client.get("/internal/v1/health", headers={"X-Service-Token": "wrong"}).status_code == 401

def test_readiness_success(monkeypatch):
    monkeypatch.setenv("ML_SERVICE_TOKEN", "test-token")
    monkeypatch.setattr(main, "check_database", lambda: None)
    monkeypatch.setattr(main, "model_info", lambda *_: {"status": "NOT_TRAINED"})
    response = client.get("/internal/v1/health", headers={"X-Service-Token": "test-token"})
    assert response.status_code == 200
    assert response.json()["model"] == "NOT_TRAINED"

def test_database_failure_sanitized(monkeypatch):
    monkeypatch.setenv("ML_SERVICE_TOKEN", "test-token")
    monkeypatch.setattr(main, "check_database", fail)
    response = client.get("/internal/v1/health", headers={"X-Service-Token": "test-token"})
    assert response.status_code == 503
    assert "secret" not in response.text
