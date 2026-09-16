import pytest

from app.server import server_options


def test_dev_starts_without_tls(monkeypatch):
    monkeypatch.setenv("ML_PROFILE", "dev")
    assert "ssl_certfile" not in server_options()


def test_unknown_profile_does_not_silently_use_http(monkeypatch):
    monkeypatch.setenv("ML_PROFILE", "production")
    with pytest.raises(RuntimeError, match="ML_PROFILE"):
        server_options()


@pytest.fixture
def production(monkeypatch, tmp_path):
    monkeypatch.setenv("ML_PROFILE", "prod")
    for name in ("ML_DB_HOST", "DB_USERNAME", "DB_PASSWORD", "ML_SERVICE_TOKEN"):
        monkeypatch.setenv(name, "test-value")
    for name in ("TLS_CERTIFICATE", "TLS_PRIVATE_KEY"):
        path = tmp_path / name
        path.write_text("placeholder; certificate parsing is performed by Uvicorn")
        monkeypatch.setenv(name, str(path))


@pytest.mark.parametrize("name", ["ML_DB_HOST", "DB_USERNAME", "DB_PASSWORD", "ML_SERVICE_TOKEN",
                                  "TLS_CERTIFICATE", "TLS_PRIVATE_KEY"])
def test_production_requires_configuration(production, monkeypatch, name):
    monkeypatch.delenv(name)
    with pytest.raises(RuntimeError, match=name):
        server_options()


def test_production_requires_existing_key(production, monkeypatch):
    monkeypatch.setenv("TLS_PRIVATE_KEY", "missing-private-key.pem")
    with pytest.raises(RuntimeError, match="TLS_PRIVATE_KEY"):
        server_options()


def test_production_enables_tls(production):
    options = server_options()
    assert options["ssl_certfile"]
    assert options["ssl_keyfile"]
    assert options["proxy_headers"] is False


@pytest.mark.parametrize("name,value", [("DB_PASSWORD", "virtual_app_password"),
                                        ("ML_SERVICE_TOKEN", "change-me-ml-token")])
def test_production_rejects_demo_values(production, monkeypatch, name, value):
    monkeypatch.setenv(name, value)
    with pytest.raises(RuntimeError, match=name) as error:
        server_options()
    assert value not in str(error.value)
