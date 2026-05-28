package com.trackit.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.GmailScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Configuration
public class GmailConfig {

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Value("${google.credentials.folder:.credentials}")
    private String credentialsFolder;

    @Bean
    public GoogleAuthorizationCodeFlow googleAuthorizationCodeFlow() throws IOException, GeneralSecurityException {
        return new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                clientId,
                clientSecret,
                Collections.singletonList(GmailScopes.GMAIL_READONLY))
                .setDataStoreFactory(new FileDataStoreFactory(new File(credentialsFolder)))
                .setAccessType("offline")
                .build();
    }
}
