package com.udhav.impactradar.api;

import com.udhav.impactradar.analysis.AnalysisOrchestrator;
import com.udhav.impactradar.analysis.ImpactReport;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
@Validated
public class AnalysisController {

    private final AnalysisOrchestrator orchestrator;

    public AnalysisController(AnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(value = "/openapi-diff", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResponse analyzeOpenApiDiff(
        @RequestParam(value = "beforeFile", required = false) MultipartFile beforeFile,
        @RequestParam(value = "afterFile", required = false) MultipartFile afterFile,
        @RequestParam(value = "providerService", required = false) String providerService,
        @RequestParam(value = "useDemo", defaultValue = "false") boolean useDemo
    ) {
        AnalysisOrchestrator.AnalysisResult result = orchestrator.analyze(beforeFile, afterFile, providerService, useDemo);
        return new AnalysisResponse(result.report(), result.markdown());
    }

    @PostMapping("/markdown")
    public ResponseEntity<String> markdownOnly(@RequestParam @NotBlank String markdown) {
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_MARKDOWN)
            .header("Content-Disposition", "attachment; filename=impact-report.md")
            .body(markdown);
    }

    public record AnalysisResponse(ImpactReport report, String markdown) {
    }
}
