package com.buct.adminbackend.service;

public interface ImageModerationService {

    int scoreImage(String contentUrl, String contentText);
}
