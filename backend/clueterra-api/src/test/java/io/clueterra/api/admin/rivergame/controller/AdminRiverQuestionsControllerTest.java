package io.clueterra.api.admin.rivergame.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.clueterra.api.admin.rivergame.dto.RiverQuestionPreviewPage;
import io.clueterra.api.admin.rivergame.service.RiverGeneratedContentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminRiverQuestionsControllerTest {

    @Test
    void readsAdminQuestionPageFromApiService() throws Exception {
        RiverGeneratedContentService service = mock(RiverGeneratedContentService.class);
        when(service.findQuestions("DE", "Rhine", "ACTIVE", "LONGEST", 2, 24))
                .thenReturn(new RiverQuestionPreviewPage(List.of(), 3, 2, 24));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminRiverQuestionsController(service)).build();

        mvc.perform(get("/api/admin/rivers/questions")
                        .param("countryCode", "de")
                        .param("search", "Rhine")
                        .param("status", "ACTIVE")
                        .param("sort", "LONGEST")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(2));

        verify(service).findQuestions("DE", "Rhine", "ACTIVE", "LONGEST", 2, 24);
    }
}
