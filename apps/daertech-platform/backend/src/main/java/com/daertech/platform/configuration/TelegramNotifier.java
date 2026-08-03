package com.daertech.platform.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class TelegramNotifier {
    private final RestClient rest = RestClient.create();
    private final String token;
    private final String chatId;

    public TelegramNotifier(@Value("${app.telegram.bot-token:}") String token,
                            @Value("${app.telegram.chat-id:}") String chatId) {
        this.token = token; this.chatId = chatId;
    }

    public void send(String message) {
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) return;
        try {
            rest.post().uri("https://api.telegram.org/bot" + token + "/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", message, "disable_web_page_preview", true))
                .retrieve().toBodilessEntity();
        } catch (Exception ignored) {
            // Notification failures must not invalidate an already committed configuration change.
        }
    }
}
