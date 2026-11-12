package io.clueterra.questions.ai.service.review;

import io.clueterra.questions.ai.model.review.AiReviewCoverage;
import io.clueterra.questions.ai.repository.AiReviewQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiReviewProgressService {

    private final AiReviewQueueRepository reviewQueueRepository;
    public AiReviewCoverage getCoverage() {
        return reviewQueueRepository.findReviewCoverage();
    }
}
