package com.sefujo.resume;

import com.sefujo.common.exception.InvalidFileException;
import com.sefujo.resume.dto.UploadResumeResponse;
import com.sefujo.resume.entity.Resume;
import com.sefujo.storage.FileStorage;
import com.sefujo.user.User;
import com.sefujo.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class ResumeService {
    private final ResumeRepository resumeRepository;
    private final FileStorage fileStorage;
    private final UserService userService;


    private UploadResumeResponse toResponse(Resume resume) {
        long resumeId = resume.getId();
        String fileName = resume.getFileName();
        Long fileSize = resume.getFileSize();
        String fileType = resume.getContentType();
        UploadResumeResponse response = new UploadResumeResponse(
                resumeId, fileName, fileSize, fileType
        );
        return response;
    }

    @Transactional
    public UploadResumeResponse uploadResume(Long userId, MultipartFile file) {
        if(!isValidPdf(file)) {
            throw new InvalidFileException("Invalid file");
        }
        String key = fileStorage.save(userId, file);
        User user = userService.getUserById(userId);
        try{
            Resume resume = new Resume(user, key, file.getOriginalFilename(), file.getSize(), file.getContentType());
            return toResponse(resumeRepository.saveAndFlush(resume));
        } catch (RuntimeException e) {
            fileStorage.delete(key);
            throw e; // error so the user get the message
        }
    }

    private boolean isValidPdf(MultipartFile file) {
        // 1. Check for null and empty
        if (file == null || file.isEmpty()) {
            return false;
        }

        // 2. Check MIME type
        String contentType = file.getContentType();
        if (contentType != null && contentType.equals("application/pdf")) {
            return true;
        }

        // Optional fallback: check file name extension if content type is missing/unreliable
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            return true;
        }

        return false;
    }
}
