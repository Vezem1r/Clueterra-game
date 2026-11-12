package io.clueterra.api.quizgame.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.clueterra.api.achievements.engine.TemplateBadgeEngine;
import io.clueterra.api.assets.service.FlagAssetPayload;
import io.clueterra.api.assets.service.FlagAssetService;
import io.clueterra.api.auth.repository.AuthJdbcRepository;
import io.clueterra.api.auth.repository.UserRow;
import io.clueterra.api.exception.LegalAcceptanceRequiredException;
import io.clueterra.api.flaggame.service.FlagSvgAnonymizer;
import io.clueterra.api.legal.service.LegalAcceptanceService;
import io.clueterra.api.quizgame.dto.QuizAnswerDto;
import io.clueterra.api.quizgame.dto.QuizAnswerResultDto;
import io.clueterra.api.quizgame.dto.QuizDestinationDto;
import io.clueterra.api.quizgame.dto.QuizOptionDto;
import io.clueterra.api.quizgame.dto.QuizPlayerStatsDto;
import io.clueterra.api.quizgame.dto.QuizQuestionDto;
import io.clueterra.api.quizgame.dto.QuizSessionDto;
import io.clueterra.api.quizgame.dto.QuizStopDto;
import io.clueterra.api.quizgame.dto.QuizSubjectDto;
import io.clueterra.api.quizgame.model.QuizAnswerOutcome;
import io.clueterra.api.quizgame.model.QuizGuestStartResult;
import io.clueterra.api.quizgame.model.QuizQuestionRow;
import io.clueterra.api.quizgame.model.QuizStopRow;
import io.clueterra.api.quizgame.repository.QuizGameJdbcRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizGameService {

    private static final String FLAG_URL = "/api/quiz-game/stops/%d/questions/%d/options/%d/flag.svg";
    private static final String TYPE = "type";
    private static final String ISO = "iso";
    private static final String NAMES = "names";
    private static final String COUNTRY = "COUNTRY";
    private static final String FLAG = "FLAG";
    private static final String SHAPE = "SHAPE";
    private static final String SHAPE_PATH = "shape";
    private static final String NUMBER = "NUMBER";
    private static final String TEXT = "TEXT";
    private static final int TRAIL_LIMIT = 60;

    private final QuizGameJdbcRepository repository;
    private final AuthJdbcRepository authRepository;
    private final LegalAcceptanceService legalAcceptanceService;
    private final TemplateBadgeEngine badgeEngine;
    private final FlagAssetService flagAssetService;
    private final FlagSvgAnonymizer anonymizer;
    private final ObjectMapper mapper;

    @Transactional
    public QuizGuestStartResult startGuest(String guestToken, String termsVersion, String privacyVersion) {
        legalAcceptanceService.validateSubmittedVersions(termsVersion, privacyVersion);
        UserRow guest = Optional.ofNullable(guestToken)
                .filter(token -> !token.isBlank())
                .flatMap(authRepository::findByGuestToken)
                .orElseGet(() -> {
                    long userId = authRepository.insertGuestUser(termsVersion, privacyVersion);
                    return authRepository.findById(userId).orElseThrow();
                });
        authRepository.refreshGuestTokenExpiry(guest.id());
        if (!legalAcceptanceService.isCurrent(guest.termsVersionAccepted(), guest.privacyVersionAccepted())) {
            legalAcceptanceService.recordUserAcceptance(
                    guest.id(), termsVersion, privacyVersion, LegalAcceptanceService.SOURCE_GUEST_REACCEPT);
        }
        repository.start(guest.id());
        return new QuizGuestStartResult(guest.guestToken(), sessionOf(guest.id()));
    }

    @Transactional
    public QuizSessionDto startRegistered(long userId) {
        ensureCurrentLegalAcceptance(userId);
        repository.start(userId);
        return sessionOf(userId);
    }

    @Transactional
    public QuizSessionDto travel(long userId, long stop) {
        ensureCurrentLegalAcceptance(userId);
        repository.travel(userId, stop);
        return sessionOf(userId);
    }

    @Transactional(readOnly = true)
    public QuizSessionDto session(Long userId) {
        return Optional.ofNullable(userId)
                .map(this::sessionOf)
                .orElseGet(() -> new QuizSessionDto(repository.emptyStats(), null, List.of(), List.of(), false));
    }

    @Transactional
    public QuizAnswerDto answer(long userId, long stop, int position, int option) {
        ensureCurrentLegalAcceptance(userId);
        QuizAnswerOutcome outcome = repository.answer(userId, stop, position, option);
        if (outcome.explored()) {
            badgeEngine.evaluateForUser(userId, null);
        }
        QuizStopDto current = repository.findStop(userId)
                .map(this::toStop)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Stop changed; resume the tour"));
        QuizAnswerResultDto answer = current.questions().stream()
                .filter(question -> question.position() == position)
                .map(QuizQuestionDto::answer)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Answered quiz question is missing"));
        QuizPlayerStatsDto stats = repository.findPlayerStats(userId);
        return new QuizAnswerDto(answer, current, stats, complete(stats));
    }

    @Transactional(readOnly = true)
    public FlagAssetPayload optionFlag(long userId, long stop, int position, int option) {
        String iso2 = repository.findOptionFlag(userId, stop, position, option)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flag is not part of the current stop"));
        FlagAssetPayload flag = flagAssetService.fetchFlag(iso2);
        return new FlagAssetPayload(anonymizer.anonymize(flag.bytes()), flag.contentType());
    }

    private QuizSessionDto sessionOf(long userId) {
        QuizPlayerStatsDto stats = repository.findPlayerStats(userId);
        return new QuizSessionDto(stats, repository.findStop(userId).map(this::toStop).orElse(null),
                repository.findCountries(userId), repository.findTrail(userId, TRAIL_LIMIT), complete(stats));
    }

    private boolean complete(QuizPlayerStatsDto stats) {
        return stats.totalLevels() > 0 && stats.completedLevels() >= stats.totalLevels();
    }

    private QuizStopDto toStop(QuizStopRow row) {
        List<QuizQuestionDto> questions = questions(row.questions()).stream()
                .map(question -> toQuestion(row.stop(), question))
                .toList();
        boolean finished = questions.stream().allMatch(question -> question.answer() != null);
        QuizDestinationDto next = finished && row.next() != null
                ? new QuizDestinationDto(row.next(), row.nextMode(), row.nextLevel(), row.nextRoute())
                : null;
        return new QuizStopDto(row.stop(), row.country(), row.level(), row.levels(), row.arrivalMode(), row.fromCountryCode(),
                questions, next, finished, row.answerXp());
    }

    private QuizQuestionDto toQuestion(long stop, QuizQuestionRow row) {
        boolean answered = row.answer() != null;
        List<QuizOptionDto> options = IntStream.range(0, row.options().size())
                .mapToObj(index -> toOption(stop, row.position(), index, row.options().get(index), answered))
                .toList();
        QuizAnswerResultDto answer = Optional.ofNullable(row.answer())
                .map(given -> new QuizAnswerResultDto(given.selectedIndex(), row.answerIndex(), given.correct(),
                        given.xpAwarded(), row.fact().toString()))
                .orElse(null);
        QuizSubjectDto subject = row.subject() == null || row.subject().isNull() ? null
                : new QuizSubjectDto(row.subject().path(ISO).asText(), row.subject().path(NAMES).toString());
        return new QuizQuestionDto(row.position(), row.kind(), subject, options, answer);
    }

    private QuizOptionDto toOption(long stop, int position, int index, JsonNode option, boolean answered) {
        String type = option.path(TYPE).asText();
        String names = option.has(NAMES) ? option.get(NAMES).toString() : null;
        String revealedIso = answered ? option.path(ISO).asText() : null;
        return switch (type) {
            case COUNTRY -> new QuizOptionDto(type, option.path(ISO).asText(), names, null, null, null, null);
            case FLAG -> new QuizOptionDto(type, revealedIso, null, FLAG_URL.formatted(stop, position, index), null, null, null);
            case SHAPE -> new QuizOptionDto(type, revealedIso, null, null, option.path(SHAPE_PATH).asText(), null, null);
            case NUMBER -> new QuizOptionDto(type, null, null, null, null, option.path("value").asInt(), null);
            case TEXT -> new QuizOptionDto(type, null, null, null, null, null, option.path("text").asText());
            default -> new QuizOptionDto(type, null, names, null, null, null, null);
        };
    }

    private List<QuizQuestionRow> questions(String json) {
        try {
            return mapper.readValue(json,
                    mapper.getTypeFactory().constructCollectionType(List.class, QuizQuestionRow.class));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot decode quiz questions", error);
        }
    }

    private void ensureCurrentLegalAcceptance(long userId) {
        UserRow user = authRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Player is unavailable"));
        if (!legalAcceptanceService.isCurrent(user.termsVersionAccepted(), user.privacyVersionAccepted())) {
            throw new LegalAcceptanceRequiredException();
        }
    }
}
