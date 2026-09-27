package com.virtualenterprise.app.service;

import com.virtualenterprise.app.integration.AttritionMlClient;
import com.virtualenterprise.app.integration.AttritionMlClient.MlImportResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class MlDatasetImportService {
    private final AttritionMlClient client;
    private final AuditTrailService audit;
    private final long maxBytes;

    public MlDatasetImportService(AttritionMlClient client, AuditTrailService audit,
                                  @Value("${ml.import-max-bytes:5242880}") long maxBytes) {
        this.client = client;
        this.audit = audit;
        this.maxBytes = maxBytes;
    }

    public MlImportResult importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "EMPTY_DATASET", "Выберите непустой CSV-файл.");
        }
        String filename = safeFilename(file.getOriginalFilename());
        if (!filename.toLowerCase().endsWith(".csv")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "CSV_FILE_REQUIRED", "Поддерживаются только файлы CSV.");
        }
        if (file.getSize() > maxBytes) {
            throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE, "DATASET_TOO_LARGE",
                    "Размер CSV-файла превышает 5 МБ.");
        }
        try {
            MlImportResult result = client.importDataset(filename, file.getBytes());
            if (result == null) {
                throw new RestClientException("Empty ML service response");
            }
            audit.record("ML_DATASET_IMPORTED", "ML_IMPORT_BATCH", null, null, Map.of(
                    "batchId", result.batchId(), "fileName", result.fileName(),
                    "rowCount", result.rowCount(), "validCount", result.validCount(),
                    "invalidCount", result.invalidCount(), "status", result.status()));
            return result;
        } catch (IOException ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "DATASET_READ_ERROR", "Не удалось прочитать CSV-файл.");
        } catch (RestClientException ex) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "ML_SERVICE_UNAVAILABLE",
                    "Сервис импорта ML-данных временно недоступен.");
        }
    }

    private String safeFilename(String filename) {
        String value = filename == null ? "dataset.csv" : filename.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        return value.isBlank() ? "dataset.csv" : value.substring(0, Math.min(value.length(), 255));
    }
}
