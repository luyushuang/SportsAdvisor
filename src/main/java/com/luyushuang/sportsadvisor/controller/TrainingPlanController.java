package com.luyushuang.sportsadvisor.controller;

import com.luyushuang.sportsadvisor.service.TrainingPlanOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/training-plan")
public class TrainingPlanController {

    private final TrainingPlanOrchestrator orchestrator;

    public TrainingPlanController(TrainingPlanOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/create")
    public ResponseEntity<TrainingPlanOrchestrator.TrainingPlanResponse> createPlan(
            @RequestBody CreatePlanRequest request) {
        var response = orchestrator.createPlan(request.userId(), request.userRequest());
        return ResponseEntity.ok(response);
    }

    public record CreatePlanRequest(String userId, String userRequest) {
    }
}