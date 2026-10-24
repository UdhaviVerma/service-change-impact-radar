package com.udhav.impactradar.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsBadRequestWhenFilesMissing() throws Exception {
        mockMvc.perform(multipart("/api/analysis/openapi-diff"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void runsDemoAnalysisWithoutFiles() throws Exception {
        mockMvc.perform(multipart("/api/analysis/openapi-diff")
                .param("useDemo", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.report.workspaceId").value("demo-microservices"));
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        MockMultipartFile before = new MockMultipartFile("beforeFile", "before.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());
        MockMultipartFile after = new MockMultipartFile("afterFile", "after.yaml", "application/x-yaml", "openapi: 3.0.3".getBytes());

        mockMvc.perform(multipart("/api/analysis/openapi-diff")
                .file(before)
                .file(after))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }
}
