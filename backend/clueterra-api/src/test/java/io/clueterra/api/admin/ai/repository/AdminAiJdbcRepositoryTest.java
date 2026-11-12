package io.clueterra.api.admin.ai.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.clueterra.api.admin.ai.dto.AiChangeSetActionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAiJdbcRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private AdminAiJdbcRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AdminAiJdbcRepository(jdbc, new ObjectMapper());
    }

    @Test
    void bulkRejectRequeuesHintAfterTheDecisionFunction() {
        AiChangeSetActionResponse response = new AiChangeSetActionResponse("10", "REJECTED", 1, 0, 1, "1 item(s) rejected");
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AiChangeSetActionResponse>>any())).thenReturn(response);

        repository.decideChangeItems(10L, List.of("HINT_UPDATE"), "REJECTED");

        verify(jdbc).update(argThat(sql -> sql.contains("review_status = 'FAILED'")), any(SqlParameterSource.class));
    }

    @Test
    void bulkApproveDoesNotRequeueHints() {
        AiChangeSetActionResponse response = new AiChangeSetActionResponse("10", "DRAFT", 1, 0, 0, "1 item(s) approved");
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AiChangeSetActionResponse>>any())).thenReturn(response);

        repository.decideChangeItems(10L, List.of("HINT_UPDATE"), "APPROVED");

        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }
}
