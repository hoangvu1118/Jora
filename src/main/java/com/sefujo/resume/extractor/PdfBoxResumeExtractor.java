package com.sefujo.resume.extractor;

import com.sefujo.common.exception.FileReadingException;
import com.sefujo.resume.entity.Resume;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class PdfBoxResumeExtractor implements ResumeExtractor {

    @Override
    public String extractResume(InputStream pdf) {
        try (PDDocument document = Loader.loadPDF(pdf.readAllBytes())) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new FileReadingException("Cannot read the file", e);
        }
    }
}
