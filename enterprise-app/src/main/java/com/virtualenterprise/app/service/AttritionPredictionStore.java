package com.virtualenterprise.app.service;

import com.virtualenterprise.app.attrition.prediction.StoredAttritionPrediction;
import com.virtualenterprise.app.integration.AttritionPredictionGateway.Prediction;
import com.virtualenterprise.app.repository.AttritionPredictionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AttritionPredictionStore {
    private final AttritionPredictionRepository repository;

    public AttritionPredictionStore(AttritionPredictionRepository repository) { this.repository = repository; }

    @Transactional
    public StoredAttritionPrediction save(Prediction prediction, LocalDateTime calculatedAt) {
        return repository.save(prediction, calculatedAt);
    }

    @Transactional(readOnly = true)
    public Optional<StoredAttritionPrediction> latest(Long employeeId) {
        return repository.findLatest(employeeId);
    }
}
