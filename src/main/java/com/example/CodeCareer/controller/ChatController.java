package com.example.CodeCareer.controller;


import com.example.CodeCareer.domain.request.ChatRequest;
import com.example.CodeCareer.domain.response.Chat.ChatResponse;
import com.example.CodeCareer.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping
    public ChatResponse handleChat(@RequestBody ChatRequest request) {
        String userMessage = request.getMessage();
        String reply = chatService.processMessage(userMessage);
        return new ChatResponse(reply);
    }
}