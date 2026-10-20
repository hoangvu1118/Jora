package com.sefujo.resume;

import com.sefujo.resume.content.ResumeContent;
import com.sefujo.resume.dto.UploadResumeResponse;
import com.sefujo.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

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

    @PostMapping("/{resumeId}/version")
    public ResponseEntity<ResumeContent> structureVersion(@PathVariable long resumeId) {
        long userId = userService.getCurrentUserId();
        String rawText = resumeService.extractResume(userId, resumeId);
        ResumeContent structuredContent = resumeService.parseResumeContent(rawText);
        return ResponseEntity.status(HttpStatus.OK).body(jsonMapper.convertValue(structuredContent, ResumeContent.class));
    }

}
