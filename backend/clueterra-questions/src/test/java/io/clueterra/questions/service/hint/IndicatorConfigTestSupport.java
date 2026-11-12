package io.clueterra.questions.service.hint;

import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

public final class IndicatorConfigTestSupport {

    private IndicatorConfigTestSupport() {
    }

    public static void installTemplates(Map<String, String> templates) {
        IndicatorConfigService service = Mockito.mock(IndicatorConfigService.class);
        Mockito.when(service.hasTemplate(Mockito.anyString()))
                .thenAnswer(invocation -> templates.containsKey(invocation.getArgument(0, String.class)));
        Mockito.when(service.getTemplate(Mockito.anyString()))
                .thenAnswer(invocation -> templates.get(invocation.getArgument(0, String.class)));
        Mockito.when(service.getBaseDifficulty(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(1, Integer.class));
        Mockito.when(service.getQualityScore(Mockito.anyString(), Mockito.any(BigDecimal.class)))
                .thenAnswer(invocation -> invocation.getArgument(1, BigDecimal.class));
        Mockito.when(service.getDisplayName(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1, String.class));

        ReflectionTestUtils.setField(IndicatorConfig.class, "INSTANCE", new IndicatorConfig(service));
    }

    public static void clear() {
        ReflectionTestUtils.setField(IndicatorConfig.class, "INSTANCE", null);
    }
}
