"""Readiness probe with certificate verification in production."""
import os
import ssl
import urllib.request


def check():
    production = os.getenv("ML_PROFILE", "dev") == "prod"
    host = os.getenv("ML_HEALTH_HOST", "localhost")
    scheme = "https" if production else "http"
    context = ssl.create_default_context(cafile=os.getenv("TLS_CA_FILE")) if production else None
    request = urllib.request.Request(
        f"{scheme}://{host}:8082/internal/v1/health",
        headers={"X-Service-Token": os.environ["ML_SERVICE_TOKEN"]})
    with urllib.request.urlopen(request, timeout=4, context=context) as response:
        if response.status != 200:
            raise RuntimeError("ML readiness failed")


if __name__ == "__main__":
    check()
