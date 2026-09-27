package com.virtualenterprise.app.web.api;

import com.virtualenterprise.app.integration.AttritionMlClient.MlImportResult;
import com.virtualenterprise.app.service.MlDatasetImportService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ml")
@PreAuthorize("hasRole('ADMIN')")
public class MlDatasetImportApiController {
    private final MlDatasetImportService service;

    public MlDatasetImportApiController(MlDatasetImportService service) { this.service = service; }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MlImportResult importDataset(@RequestParam("file") MultipartFile file) {
        return service.importCsv(file);
    }
}
