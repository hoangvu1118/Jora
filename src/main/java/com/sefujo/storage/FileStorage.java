package com.sefujo.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorage {
    // returns a key, e.g. "resumes/5/abc123.pdf"
    String save(Long userId, MultipartFile file) throws IOException;

    InputStream load(String key);      // give key, get the file back
}
