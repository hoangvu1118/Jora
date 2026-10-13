package com.sefujo.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileStorage {
    /**
     * Saves the file and returns its key, e.g. "resume/5/abc.pdf".
     * @throws FileStorageException if the file cannot be stored
     */
    String save(Long userId, MultipartFile file);

    InputStream load(String key);      // give key, get the file back
}
