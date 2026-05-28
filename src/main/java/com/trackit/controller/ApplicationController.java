package com.trackit.controller;

import com.trackit.dto.ApplicationRequest;
import com.trackit.dto.ApplicationResponse;
import com.trackit.dto.StatsResponse;
import com.trackit.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import com.trackit.service.GmailSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
@Tag(name = "Applications", description = "Manage job and internship applications")
public class ApplicationController {

    private final ApplicationService service;
    private final GmailSyncService gmailSyncService;

    @Autowired
    public ApplicationController(ApplicationService service, GmailSyncService gmailSyncService) {
        this.service = service;
        this.gmailSyncService = gmailSyncService;
    }

    // ---- GET all (with filters) ----

    @GetMapping
    @Operation(
        summary = "Get all applications",
        description = "Returns all applications. Optionally filter by status, type, or search query."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of applications returned successfully")
    })
    public ResponseEntity<List<ApplicationResponse>> getAll(
            @Parameter(description = "Filter by status: APPLIED, INTERVIEW, OFFER, REJECTED")
            @RequestParam(required = false) String status,

            @Parameter(description = "Filter by type: INTERNSHIP, JOB")
            @RequestParam(required = false) String type,

            @Parameter(description = "Search by role or company name")
            @RequestParam(required = false) String search) {

        return ResponseEntity.ok(service.getAll(status, type, search));
    }

    // ---- GET by ID ----

    @GetMapping("/{id}")
    @Operation(summary = "Get application by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Application found"),
        @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<ApplicationResponse> getById(
            @Parameter(description = "Application ID") @PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    // ---- POST create ----

    @PostMapping
    @Operation(summary = "Create a new application")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Application created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody ApplicationRequest request) {
        ApplicationResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ---- PUT update (full replace) ----

    @PutMapping("/{id}")
    @Operation(summary = "Update an application (full replace)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Application updated successfully"),
        @ApiResponse(responseCode = "404", description = "Application not found"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<ApplicationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    // ---- PATCH status only ----

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update only the status of an application")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status updated"),
        @ApiResponse(responseCode = "404", description = "Application not found"),
        @ApiResponse(responseCode = "400", description = "Invalid status value")
    })
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("'status' field is required in the request body");
        }
        return ResponseEntity.ok(service.updateStatus(id, status));
    }

    // ---- DELETE ----

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an application")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Application deleted"),
        @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ---- GET stats ----

    @GetMapping("/stats")
    @Operation(
        summary = "Get dashboard stats",
        description = "Returns count of applications by status and type"
    )
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(service.getStats());
    }

    // ---- POST gmail-sync ----

    @PostMapping("/gmail-sync")
    @Operation(
        summary = "Trigger Gmail API internship synchronization",
        description = "Connects to the authorized user's Gmail inbox and imports matches"
    )
    public ResponseEntity<Map<String, Object>> syncGmail() throws java.io.IOException {
        int count = gmailSyncService.syncApplications();
        Map<String, Object> response = new HashMap<>();
        response.put("count", count);
        response.put("message", count + " applications synchronized successfully.");
        return ResponseEntity.ok(response);
    }
}
