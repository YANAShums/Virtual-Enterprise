package com.virtualenterprise.app.service;

import com.virtualenterprise.app.integration.MlWorkflowClient;
import com.virtualenterprise.app.integration.MlWorkflowClient.Candidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class MlWorkflowService {
    private final MlWorkflowClient client;
    private final AuditTrailService audit;
    private final long maxBytes;
    public MlWorkflowService(MlWorkflowClient client, AuditTrailService audit,
                             @Value("${ml.import-max-bytes:5242880}") long maxBytes) {
        this.client = client; this.audit = audit; this.maxBytes = maxBytes;
    }
    public Overview overview() {
        try { return new Overview(client.models(), client.stats(), null); }
        catch (RestClientException ex) { return new Overview(List.of(), List.of(), "ML-сервис временно недоступен. Сохранённые прогнозы доступны в разделе текучести."); }
    }
    public Candidate train() {
        Candidate value = invoke(client::train);
        audit.record("ML_CANDIDATE_TRAINED", "ML_MODEL_VERSION", null, null,
                Map.of("version", value.version(), "status", value.status(), "gatePassed", value.gatePassed()));
        return value;
    }
    public Candidate activate(String version) {
        Candidate value = invoke(() -> client.activate(version));
        audit.record("ML_MODEL_ACTIVATED", "ML_MODEL_VERSION", null, null, Map.of("version", value.version()));
        return value;
    }
    public Map<String,Object> importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new AppException(HttpStatus.BAD_REQUEST,"EMPTY_DATASET","Выберите CSV-файл.");
        if (file.getSize() > maxBytes) throw new AppException(HttpStatus.PAYLOAD_TOO_LARGE,"DATASET_TOO_LARGE","Файл превышает допустимый размер.");
        String name = file.getOriginalFilename() == null ? "dataset.csv" : file.getOriginalFilename().replace('\\','/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".csv"))
            throw new AppException(HttpStatus.BAD_REQUEST,"CSV_FILE_REQUIRED","Поддерживаются CSV-файлы.");
        try {
            String filename = name;
            byte[] bytes = file.getBytes();
            Map<String,Object> result = invoke(() -> client.importCsv(filename, bytes));
            audit.record("ML_V2_DATASET_IMPORTED", "ML_DATASET", null, null,
                    Map.of("imported", result.get("imported"), "failed", result.get("failed")));
            return result;
        } catch (IOException ex) { throw new AppException(HttpStatus.BAD_REQUEST,"DATASET_READ_ERROR","Не удалось прочитать файл."); }
    }
    public Map<String,Object> synchronize() {
        Map<String,Object> result = invoke(client::sync);
        audit.record("ML_SNAPSHOTS_SYNCHRONIZED", "ML_DATASET", null, null, result);
        return result;
    }
    private <T> T invoke(Supplier<T> operation) {
        try { T value = operation.get(); if (value == null) throw new RestClientException("Empty ML response"); return value; }
        catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 422) throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY,"ML_DATA_INVALID",
                    "Недостаточно корректной размеченной истории: нужны оба класса и независимые сотрудники. Проверьте журнал модели и CSV.");
            if (ex.getStatusCode().value() == 409) throw new AppException(HttpStatus.CONFLICT,"ML_CONFLICT",
                    "Операция отклонена: модель не прошла проверку качества или другая операция уже выполняется.");
            if (ex.getStatusCode().value() == 404) throw new AppException(HttpStatus.NOT_FOUND,"ML_MODEL_NOT_FOUND","Версия модели не найдена.");
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,"ML_SERVICE_UNAVAILABLE","ML-сервис временно недоступен.");
        } catch (RestClientException ex) { throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,"ML_SERVICE_UNAVAILABLE","ML-сервис временно недоступен."); }
    }
    public record Overview(List<Candidate> candidates, List<Map<String,Object>> stats, String error) {}
}
