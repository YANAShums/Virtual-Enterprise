FROM python:3.12-slim AS runtime-base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY attrition-ml-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --create-home appuser
COPY attrition-ml-service/app ./app
RUN mkdir -p /data/models && chown appuser:appuser /data/models

FROM runtime-base AS test
COPY attrition-ml-service/requirements-dev.txt .
RUN pip install --no-cache-dir -r requirements-dev.txt
COPY attrition-ml-service/tests ./tests
USER appuser
CMD ["python", "-m", "pytest", "tests", "-q", "-p", "no:cacheprovider"]

FROM runtime-base AS runtime
USER appuser
EXPOSE 8082
CMD ["python", "-m", "app.server"]
