package io.clueterra.questions.config;

public record ImageQuotaRule() {

    public int compute(int totalHints) {
        return totalHints / 10 + (totalHints % 10 >= 6 ? 1 : 0);
    }
}
