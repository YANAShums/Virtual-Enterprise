package com.virtualenterprise.app.integration;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Synchronous multipart body; does not require Reactive Streams on the MVC classpath. */
final class CsvMultipartBody {
    private CsvMultipartBody() {}

    static MultiValueMap<String, Object> create(String filename, byte[] content) {
        var resource = new ByteArrayResource(content) {
            @Override public String getFilename() { return filename; }
        };
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, headers));
        return body;
    }
}
