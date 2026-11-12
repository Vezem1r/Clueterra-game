package io.clueterra.questions.service.deduplication.dto;

import io.clueterra.questions.service.hint.lens.CandidateHint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record DeduplicationResult(List<CandidateHint> accepted, List<RejectedHint> rejected,
                                  List<CandidateHint> toRefresh,
                                  int totalInput, int totalAccepted, int totalRejected) {

    public static DeduplicationResult of(List<CandidateHint> accepted, List<RejectedHint> rejected) {
        return new DeduplicationResult(accepted, rejected, List.of(),
                accepted.size() + rejected.size(), accepted.size(), rejected.size());
    }

    public static DeduplicationResult of(List<CandidateHint> accepted, List<RejectedHint> rejected,
                                         List<CandidateHint> toRefresh) {
        return new DeduplicationResult(accepted, rejected, toRefresh,
                accepted.size() + rejected.size() + toRefresh.size(), accepted.size(), rejected.size());
    }

    public Map<String, Object> toLogDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("total_input", totalInput);
        details.put("total_accepted", totalAccepted);
        details.put("total_rejected", totalRejected);

        Map<RejectionReason, Long> reasonCounts = rejected.stream()
                .collect(Collectors.groupingBy(RejectedHint::reason, Collectors.counting()));
        details.put("rejection_summary", reasonCounts);

        List<Map<String, Object>> rejectionDetails = new ArrayList<>();
        for (RejectedHint rejection : rejected) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("rejected_text", truncate(rejection.hint().text()));
            entry.put("reason", rejection.reason().name());
            entry.put("details", rejection.details());
            entry.put("lens", rejection.hint().lensName());
            entry.put("indicator", rejection.hint().sourceIndicator());
            if (rejection.blockingHintText() != null) {
                entry.put("blocked_by", truncate(rejection.blockingHintText()));
            }
            if (rejection.similarityScore() != null) {
                entry.put("similarity", rejection.similarityScore());
            }
            rejectionDetails.add(entry);
        }
        details.put("rejections", rejectionDetails);

        return details;
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= 100) {
            return text;
        }
        return text.substring(0, 100 - 3) + "...";
    }
}
