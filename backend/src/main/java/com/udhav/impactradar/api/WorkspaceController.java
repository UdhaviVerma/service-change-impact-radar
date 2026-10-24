package com.udhav.impactradar.api;

import com.udhav.impactradar.domain.DemoWorkspace;
import com.udhav.impactradar.repository.DemoWorkspaceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final DemoWorkspaceRepository repository;

    public WorkspaceController(DemoWorkspaceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/demo")
    public DemoWorkspace demoWorkspace() {
        return repository.getDemoWorkspace();
    }
}
