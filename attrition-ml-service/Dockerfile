FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY attrition-ml-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --create-home appuser
COPY attrition-ml-service/app ./app
RUN mkdir -p /data/models && chown appuser:appuser /data/models
USER appuser
EXPOSE 8082
CMD ["python", "-m", "app.server"]
