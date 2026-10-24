package com.udhav.impactradar.analysis;

import com.udhav.impactradar.domain.DemoWorkspace;
import com.udhav.impactradar.repository.DemoWorkspaceRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class AnalysisOrchestrator {

    private final OpenApiParser parser;
    private final ImpactAnalyzerService analyzerService;
    private final DemoWorkspaceRepository workspaceRepository;
    private final MarkdownReportBuilder markdownBuilder;

    public AnalysisOrchestrator(
        OpenApiParser parser,
        ImpactAnalyzerService analyzerService,
        DemoWorkspaceRepository workspaceRepository,
        MarkdownReportBuilder markdownBuilder
    ) {
        this.parser = parser;
        this.analyzerService = analyzerService;
        this.workspaceRepository = workspaceRepository;
        this.markdownBuilder = markdownBuilder;
    }

    public AnalysisResult analyze(
        MultipartFile beforeFile,
        MultipartFile afterFile,
        String providerService,
        boolean useDemo
    ) {
        String beforeContent;
        String afterContent;

        if (useDemo) {
            beforeContent = readResource("demo/customer-before.yaml");
            afterContent = readResource("demo/customer-after.yaml");
        } else {
            validateFile(beforeFile, "beforeFile");
            validateFile(afterFile, "afterFile");
            beforeContent = readMultipart(beforeFile);
            afterContent = readMultipart(afterFile);
        }

        OpenApiSpec before = parser.parse(beforeContent);
        OpenApiSpec after = parser.parse(afterContent);

        DemoWorkspace workspace = workspaceRepository.getDemoWorkspace();
        String resolvedProvider = (providerService == null || providerService.isBlank()) ? workspace.sampleProvider() : providerService;

        ImpactReport report = analyzerService.analyze(workspace.id(), resolvedProvider, before, after, workspace);
        String markdown = markdownBuilder.build(report);
        return new AnalysisResult(report, markdown);
    }

    private void validateFile(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required unless useDemo=true.");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!(filename.endsWith(".json") || filename.endsWith(".yaml") || filename.endsWith(".yml"))) {
            throw new IllegalArgumentException(fieldName + " must be a .json, .yaml, or .yml file.");
        }

        if (file.getSize() > 1_000_000) {
            throw new IllegalArgumentException(fieldName + " exceeds 1MB limit.");
        }
    }

    private String readMultipart(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read uploaded file: " + file.getOriginalFilename());
        }
    }

    private String readResource(String classpath) {
        try {
            ClassPathResource resource = new ClassPathResource(classpath);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing demo resource: " + classpath);
        }
    }

    public record AnalysisResult(ImpactReport report, String markdown) {}
}
