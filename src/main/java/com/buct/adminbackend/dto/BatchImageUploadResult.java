package com.buct.adminbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchImageUploadResult {

    private int uploaded;
    private int replaced;
    private int skipped;
    private final List<String> details = new ArrayList<>();

    public int getUploaded() {
        return uploaded;
    }

    public void setUploaded(int uploaded) {
        this.uploaded = uploaded;
    }

    public int getReplaced() {
        return replaced;
    }

    public void setReplaced(int replaced) {
        this.replaced = replaced;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public List<String> getDetails() {
        return details;
    }

    public void addDetail(String line) {
        details.add(line);
    }
}
