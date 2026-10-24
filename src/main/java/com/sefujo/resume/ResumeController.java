package com.sefujo.resume;

import com.sefujo.resume.dto.ResumeVersionBriefResponse;
import com.sefujo.resume.dto.UploadResumeResponse;
import com.sefujo.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/resume")
@RequiredArgsConstructor
public class ResumeController {
    private final ResumeService resumeService;
    private final UserService userService;
    private final JsonMapper jsonMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResumeResponse> resume(
            @RequestParam("file")MultipartFile file
            ) {
        long userId = userService.getCurrentUserId();
        UploadResumeResponse response = resumeService.uploadResume(userId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{resumeId}/extract")
    public ResponseEntity<String> extractResume(@PathVariable long resumeId) {
        long userId = userService.getCurrentUserId();
        String rawText = resumeService.extractResume(userId, resumeId);
        return ResponseEntity.status(HttpStatus.OK).body(rawText);
    }

    @PostMapping("/{resumeId}/versions")
    public ResponseEntity<ResumeVersionBriefResponse> structureVersion(@PathVariable long resumeId) {
        long userId = userService.getCurrentUserId();
        Optional<ResumeVersionBriefResponse> existing = resumeService.findBaseResume(userId, resumeId);
        if(existing.isPresent()) {
            return  ResponseEntity.status(HttpStatus.OK).body(existing.get());
        }
        // Call LLM to create a BASE version out of rawText
        ResumeVersionBriefResponse response = resumeService.createResumeVersion(userId, resumeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{resumeId}/versions/{versionId}")
    public ResponseEntity<ResumeVersionBriefResponse> getVersion(@PathVariable long resumeId, @PathVariable long versionId) {
        long userId = userService.getCurrentUserId();
        ResumeVersionBriefResponse response = resumeService.getResumeVersion(userId, resumeId, versionId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/{resumeId}/archive")
    public ResponseEntity<Void> archiveResume(@PathVariable long resumeId) {
        long userId = userService.getCurrentUserId();
        resumeService.archiveResume(userId, resumeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{resumeId}/tailor/{jobId}")
    public ResponseEntity<ResumeVersionBriefResponse> tailorResume(
            @PathVariable long resumeId, @PathVariable long jobId) {

        long userId = userService.getCurrentUserId();
        Optional<ResumeVersionBriefResponse> response = resumeService.tailorResume(userId, resumeId, jobId);
        if(response.isPresent()) {
            return ResponseEntity.status(HttpStatus.OK).body(response.get());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response.get());
    }

}
