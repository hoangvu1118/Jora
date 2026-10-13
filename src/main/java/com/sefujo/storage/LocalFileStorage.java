package com.sefujo.storage;

import com.sefujo.common.exception.FileStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class LocalFileStorage implements FileStorage{

    private final Path rootFolder;

    public LocalFileStorage(@Value("${resume.upload.dir}") String rootFolder) {
        this.rootFolder = Paths.get(rootFolder).toAbsolutePath();
    }

    @Override
    public String save(Long userId, MultipartFile file) {
        String key = "resume/" + userId + "/" + UUID.randomUUID().toString() + ".pdf";
        Path targetPath = rootFolder.resolve(key);
        try{
            // ensure the user create the directory the first time they upload CV
            Files.createDirectories(targetPath.getParent());
            try(InputStream inputStream = file.getInputStream()){
                Files.copy(inputStream, targetPath);
            }
            return key;

        } catch(IOException e){
            throw new FileStorageException("Could not save file", e);
        }
    }

    @Override
    public InputStream load(String key) {
        Path targetPath = rootFolder.resolve(key);
        try{
            return Files.newInputStream(targetPath);
        }  catch (IOException e){
            throw new FileStorageException("Could not read file " + key, e);
        }
    }

}
