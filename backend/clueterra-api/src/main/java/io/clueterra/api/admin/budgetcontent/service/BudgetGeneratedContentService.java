package io.clueterra.api.admin.budgetcontent.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.clueterra.api.admin.budgetcontent.dto.BudgetGeneratedChallengeDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetGeneratedChallengePage;
import io.clueterra.api.admin.budgetcontent.dto.BudgetChallengePeriod;
import io.clueterra.api.admin.budgetcontent.dto.BudgetGeneratedQuestionDto;
import io.clueterra.api.admin.budgetcontent.dto.BudgetGeneratedQuestionPage;
import io.clueterra.api.admin.budgetcontent.repository.BudgetGeneratedContentFilter;
import io.clueterra.api.admin.budgetcontent.repository.BudgetGeneratedContentJdbcRepository;
import io.clueterra.api.exception.ResourceConflictException;
import io.clueterra.api.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetGeneratedContentService {

    private final BudgetGeneratedContentJdbcRepository repository;

    public BudgetGeneratedQuestionPage findQuestions(
            BudgetGeneratedContentFilter filter, int page, int size) {
        long total = repository.countQuestions(filter);
        List<BudgetGeneratedQuestionDto> content = repository.findQuestions(filter, size, page * size);
        return new BudgetGeneratedQuestionPage(content, total, page, size, totalPages(total, size));
    }

    public BudgetGeneratedChallengePage findChallenges(
            BudgetChallengePeriod period, int page, int size) {
        long total = repository.countChallenges(period);
        List<BudgetGeneratedChallengeDto> content =
                repository.findChallenges(period, size, page * size);
        return new BudgetGeneratedChallengePage(content, total, page, size, totalPages(total, size));
    }

    public void deleteUpcomingChallenge(long challengeId) {
        var result = repository.deleteUpcomingChallenge(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Budget challenge not found"));
        if (!result.deleted()) {
            if (!result.future()) {
                throw new ResourceConflictException(
                        "Today's and past Budget challenges cannot be deleted");
            }
            if (result.hasSessions()) {
                throw new ResourceConflictException(
                        "Budget challenge cannot be deleted because player sessions already exist");
            }
            throw new ResourceConflictException("Budget challenge could not be deleted");
        }
    }

    private int totalPages(long total, int size) {
        return (int) Math.ceil((double) total / size);
    }
}
