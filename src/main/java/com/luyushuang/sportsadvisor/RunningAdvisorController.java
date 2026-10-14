package com.luyushuang.sportsadvisor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/running-advisor")
public class RunningAdvisorController {

    private final RunningAdvisorService service;

    public RunningAdvisorController(RunningAdvisorService service) {
        this.service = service;
    }

    /**
     * 单轮问答
     * GET /api/running-advisor/ask?question=北京明天适合跑步吗
     */
    @GetMapping("/ask")
    public ResponseEntity<AskResponse> ask(@RequestParam @NotBlank String question) {
        String answer = service.ask(question);
        return ResponseEntity.ok(new AskResponse(answer));
    }

    /**
     * 多轮对话（带会话 ID）
     * POST /api/running-advisor/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<AskResponse> chat(@RequestBody @Valid ChatRequest request) {
        String answer = service.ask(request.conversationId(), request.question());
        return ResponseEntity.ok(new AskResponse(answer));
    }

    // ---- DTO ----
    public record ChatRequest(
            @NotBlank(message = "conversationId 不能为空") String conversationId,
            @NotBlank(message = "question 不能为空") String question
    ) {
    }

    public record AskResponse(String answer) {
    }
}
