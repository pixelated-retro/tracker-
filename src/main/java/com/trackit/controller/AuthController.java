package com.trackit.controller;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "OAuth2 flows and connection status for external providers")
public class AuthController {

    private final GoogleAuthorizationCodeFlow flow;

    @Value("${google.redirect.uri}")
    private String redirectUri;

    @Autowired
    public AuthController(GoogleAuthorizationCodeFlow flow) {
        this.flow = flow;
    }

    @GetMapping("/google/login")
    @Operation(summary = "Redirect to Google OAuth2 Consent Screen")
    public void login(HttpServletResponse response) throws IOException {
        String url = flow.newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .build();
        response.sendRedirect(url);
    }

    @GetMapping("/google/callback")
    @Operation(summary = "Handle Google OAuth2 Callback and store user credentials")
    public void callback(@RequestParam String code, HttpServletResponse response) throws IOException {
        TokenResponse tokenResponse = flow.newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();
        flow.createAndStoreCredential(tokenResponse, "user");
        response.sendRedirect("/");
    }

    @GetMapping("/google/status")
    @Operation(summary = "Get the Google OAuth2 connection status")
    public ResponseEntity<Map<String, Object>> getStatus() throws IOException {
        Credential credential = flow.loadCredential("user");
        boolean connected = credential != null && credential.getAccessToken() != null;
        Map<String, Object> status = new HashMap<>();
        status.put("connected", connected);
        return ResponseEntity.ok(status);
    }

    @PostMapping("/google/disconnect")
    @Operation(summary = "Disconnect Google account by deleting stored user credentials")
    public ResponseEntity<Void> disconnect() throws IOException {
        flow.getCredentialDataStore().delete("user");
        return ResponseEntity.ok().build();
    }
}
