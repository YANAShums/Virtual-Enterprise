package com.virtualenterprise.app.web.api;

import com.virtualenterprise.app.integration.AttritionMlClient.MlModelInfo;
import com.virtualenterprise.app.service.MlModelService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ml")
@PreAuthorize("hasRole('ADMIN')")
public class MlModelApiController {
    private final MlModelService service;

    public MlModelApiController(MlModelService service) { this.service = service; }

    @PostMapping("/train")
    MlModelInfo train() { return service.train(); }

    @GetMapping("/model")
    MlModelInfo model() { return service.currentModel(); }
}
