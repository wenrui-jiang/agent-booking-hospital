package com.atguigu.yygh.agent.controller;

import com.atguigu.yygh.agent.model.AgentSession;
import com.atguigu.yygh.agent.model.AgentMessage;
import com.atguigu.yygh.agent.model.AgentSessionSummary;
import com.atguigu.yygh.agent.model.ChatRequest;
import com.atguigu.yygh.agent.model.ChatResponse;
import com.atguigu.yygh.agent.model.PretriageReport;
import com.atguigu.yygh.agent.model.ToolCallRecord;
import com.atguigu.yygh.agent.service.AgentWorkflowService;
import com.atguigu.yygh.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private AgentWorkflowService agentWorkflowService;

    @PostMapping("chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request,
                                     @RequestHeader(value = "token", required = false) String token) {
        return Result.ok(agentWorkflowService.chat(request, token));
    }

    @GetMapping("sessions")
    public Result<List<AgentSessionSummary>> listSessions(@RequestHeader(value = "token", required = false) String token) {
        return Result.ok(agentWorkflowService.listSessions(token));
    }

    @PostMapping("session/new")
    public Result<AgentSession> newSession(@RequestHeader(value = "token", required = false) String token) {
        return Result.ok(agentWorkflowService.newSession(token));
    }

    @GetMapping("session/{sessionId}")
    public Result<AgentSession> getSession(@PathVariable String sessionId,
                                           @RequestHeader(value = "token", required = false) String token) {
        return Result.ok(agentWorkflowService.getSession(sessionId, token));
    }

    @GetMapping("session/{sessionId}/messages")
    public Result<List<AgentMessage>> getMessages(@PathVariable String sessionId,
                                                  @RequestHeader(value = "token", required = false) String token) {
        return Result.ok(agentWorkflowService.listMessages(sessionId, token));
    }

    @GetMapping("pretriage-report/{sessionId}")
    public Result<PretriageReport> getReport(@PathVariable String sessionId,
                                             @RequestHeader(value = "token", required = false) String token) {
        return Result.ok(agentWorkflowService.getReport(sessionId, token));
    }

    @PostMapping("pretriage-report/{sessionId}/confirm")
    public Result<PretriageReport> confirmReport(@PathVariable String sessionId,
                                                 @RequestHeader(value = "token", required = false) String token) {
        return Result.ok(agentWorkflowService.confirmReport(sessionId, token));
    }

    @GetMapping("tool-calls/{sessionId}")
    public Result<List<ToolCallRecord>> getToolCalls(@PathVariable String sessionId,
                                                     @RequestHeader(value = "token", required = false) String token) {
        return Result.ok(agentWorkflowService.getToolCalls(sessionId, token));
    }
}
