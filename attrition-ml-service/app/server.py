"""Explicit local/production startup; production never falls back to HTTP."""
import os
from pathlib import Path

import uvicorn


def server_options():
    profile = os.getenv("ML_PROFILE", "dev")
    if profile not in {"dev", "prod"}:
        raise RuntimeError("ML_PROFILE must be dev or prod")
    options = {"host": "0.0.0.0", "port": 8082, "proxy_headers": False}
    if profile == "prod":
        for name in ("ML_DB_HOST", "DB_USERNAME", "DB_PASSWORD", "ML_SERVICE_TOKEN",
                     "TLS_CERTIFICATE", "TLS_PRIVATE_KEY"):
            if not os.getenv(name, "").strip():
                raise RuntimeError(f"Required production setting is missing: {name}")
        for name in ("DB_PASSWORD", "ML_SERVICE_TOKEN"):
            value = os.environ[name]
            if value.startswith("change-me") or value in {"virtual_app_password", "root_password_change_me"}:
                raise RuntimeError(f"Demo value is not allowed in production: {name}")
        for name in ("TLS_CERTIFICATE", "TLS_PRIVATE_KEY"):
            if not Path(os.environ[name]).is_file():
                raise RuntimeError(f"Production TLS file is missing: {name}")
        options.update(ssl_certfile=os.environ["TLS_CERTIFICATE"],
                       ssl_keyfile=os.environ["TLS_PRIVATE_KEY"])
    return options


if __name__ == "__main__":
    uvicorn.run("app.main:app", **server_options())
