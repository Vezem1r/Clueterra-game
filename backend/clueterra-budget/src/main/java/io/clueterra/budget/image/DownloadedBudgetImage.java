package io.clueterra.budget.image;

public record DownloadedBudgetImage(byte[] bytes, String contentType, String extension) {
}
