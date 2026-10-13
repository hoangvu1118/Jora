package com.sefujo.common.exception;

public class SearchProfileAlreadyExist extends ConflictException {
    public SearchProfileAlreadyExist(String message) {
        super(message);
    }
}
