package com.trackit.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.trackit.model.Application;
import com.trackit.model.ApplicationStatus;
import com.trackit.model.ApplicationType;
import com.trackit.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GmailSyncService {

    private static final Logger log = LoggerFactory.getLogger(GmailSyncService.class);

    private final GoogleAuthorizationCodeFlow flow;
    private final ApplicationRepository repository;

    @Autowired
    public GmailSyncService(GoogleAuthorizationCodeFlow flow, ApplicationRepository repository) {
        this.flow = flow;
        this.repository = repository;
    }

    public int syncApplications() throws IOException {
        Credential credential = flow.loadCredential("user");
        if (credential == null || credential.getAccessToken() == null) {
            throw new IllegalStateException("Gmail account is not connected. Please authenticate first.");
        }

        Gmail gmail = new Gmail.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("TrackIt")
                .build();

        // Search for application confirmation emails from the past 30 days
        String query = "subject:(\"application received\" OR \"thank you for applying\" OR \"confirmation of application\")";
        ListMessagesResponse response;
        try {
            response = gmail.users().messages().list("me").setQ(query).setMaxResults(50L).execute();
        } catch (GoogleJsonResponseException e) {
            log.error("Failed to list messages from Gmail API", e);
            throw e;
        }

        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int newAppsCount = 0;
        for (Message msgSummary : messages) {
            Message message = gmail.users().messages().get("me", msgSummary.getId()).setFormat("metadata").execute();
            List<MessagePartHeader> headers = message.getPayload().getHeaders();

            String subject = "";
            String from = "";
            for (MessagePartHeader header : headers) {
                if ("Subject".equalsIgnoreCase(header.getName())) {
                    subject = header.getValue();
                } else if ("From".equalsIgnoreCase(header.getName())) {
                    from = header.getValue();
                }
            }

            if (subject.isEmpty() || from.isEmpty()) continue;

            String company = parseCompany(from);
            String role = parseRole(subject);

            // Double check if this record is already stored
            List<Application> matches = repository.searchByRoleOrCompany(company);
            boolean alreadyExists = matches.stream().anyMatch(a -> 
                a.getRole().equalsIgnoreCase(role) && a.getCompany().equalsIgnoreCase(company)
            );

            if (!alreadyExists) {
                Application app = new Application();
                app.setRole(role);
                app.setCompany(company);
                app.setType(role.toLowerCase().contains("intern") ? ApplicationType.INTERNSHIP : ApplicationType.JOB);
                app.setStatus(ApplicationStatus.APPLIED);
                app.setDateApplied(LocalDate.now());
                app.setNotes("Automatically synchronized via Gmail Integration.");
                
                repository.save(app);
                newAppsCount++;
                log.info("Automatically tracked application from Gmail: {} at {}", role, company);
            }
        }

        return newAppsCount;
    }

    private String parseCompany(String fromHeader) {
        // e.g. "Stripe Recruiting <recruiting@stripe.com>" -> "Stripe"
        // e.g. "recruiting@stripe.com" -> "Stripe"
        Pattern pattern = Pattern.compile("^(.*?)\\s*<.*?>");
        Matcher matcher = pattern.matcher(fromHeader);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            name = name.replaceAll("(?i)(Careers|Recruiting|HR|Jobs|Team|Support)", "").trim();
            if (!name.isEmpty()) {
                return capitalize(name);
            }
        }

        // Fallback: parse domain name
        Pattern emailPattern = Pattern.compile("@(.*?)\\.");
        Matcher emailMatcher = emailPattern.matcher(fromHeader);
        if (emailMatcher.find()) {
            String domain = emailMatcher.group(1);
            return capitalize(domain);
        }
        return "Unknown Company";
    }

    private String parseRole(String subjectHeader) {
        // Remove common subject prefixes
        String role = subjectHeader.replaceAll("(?i)^(Application Received|Thank you for applying|Your application|Confirmation|Confirmation of application|Thanks for applying|Job Application for|Application for)\\s*[:\\-]?\\s*", "").trim();
        if (role.isEmpty()) {
            return "Software Engineer Intern";
        }
        return capitalize(role);
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)))
              .append(word.substring(1).toLowerCase())
              .append(" ");
        }
        return sb.toString().trim();
    }
}
