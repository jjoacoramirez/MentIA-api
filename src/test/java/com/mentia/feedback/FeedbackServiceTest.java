package com.mentia.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mentia.ai.AiPurpose;
import com.mentia.ai.AiRequest;
import com.mentia.ai.AiResponse;
import com.mentia.ai.AiUnavailableException;
import com.mentia.ai.AiUnavailableException.Reason;
import com.mentia.ai.GuardedAiService;
import com.mentia.catalog.CatalogService;
import com.mentia.catalog.dto.CategoryResponse;
import com.mentia.feedback.domain.SessionFeedback;
import com.mentia.feedback.dto.FeedbackResponse;
import com.mentia.session.SessionLifecycleService;

class FeedbackServiceTest {

    private final SessionLifecycleService sessions = mock(SessionLifecycleService.class);
    private final FeedbackRepository repository = mock(FeedbackRepository.class);
    private final GuardedAiService ai = mock(GuardedAiService.class);
    private final CatalogService catalog = mock(CatalogService.class);
    private final UUID user = UUID.randomUUID();
    private final UUID session = UUID.randomUUID();
    private FeedbackService service;

    @BeforeEach
    void setUp() {
        given(catalog.activeCategories()).willReturn(List.of(new CategoryResponse("ATENCION", "Atención", "", true)));
        given(sessions.result(user, session)).willReturn(Map.of("type", "TRAINING", "itemsTotal", 6, "itemsCompleted", 6,
                "accuracy", 0.9, "categories", List.of(Map.of("category", "ATENCION", "accuracy", 0.9,
                        "difficultyLevelBefore", 3, "difficultyLevelAfter", 4))));
        given(repository.save(any())).willAnswer(call -> call.getArgument(0));
        service = new FeedbackService(sessions, repository, ai, catalog,
                new FeedbackProperties(200, 450, List.of("diagnostic", "capacidad", "cognitiv")), Clock.systemUTC());
    }

    private void aiSays(String text) {
        given(ai.generate(any(AiPurpose.class), any(AiRequest.class), any(UUID.class))).willReturn(new AiResponse(text, 80, 40));
    }

    private SessionFeedback saved() {
        ArgumentCaptor<SessionFeedback> captor = ArgumentCaptor.forClass(SessionFeedback.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void usesTheAiTextAndSavesItAsAi() {
        aiSays("¡Qué buena sesión! Subiste de nivel en Atención y mantuviste muy buena precisión.");

        FeedbackResponse response = service.getOrCreate(user, session);

        assertThat(response.source()).isEqualTo(FeedbackSource.AI);
        assertThat(response.text()).startsWith("¡Qué buena sesión!");
        assertThat(saved().getSource()).isEqualTo(FeedbackSource.AI);
    }

    @Test
    void theAiCallCountsAgainstThePersonsQuotaAndCarriesNoIdentifiers() {
        aiSays("¡Qué buena sesión! Subiste de nivel en Atención y mantuviste muy buena precisión.");
        service.getOrCreate(user, session);

        ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
        verify(ai).generate(any(AiPurpose.class), request.capture(), any(UUID.class));
        verify(ai).generate(org.mockito.ArgumentMatchers.eq(AiPurpose.USER_FEEDBACK), any(AiRequest.class),
                org.mockito.ArgumentMatchers.eq(user));
        assertThat(request.getValue().userPrompt()).doesNotContain(user.toString(), session.toString());
        assertThat(request.getValue().systemPrompt()).doesNotContain(user.toString(), session.toString());
    }

    @Test
    void fallsBackToTheBasicTextWhenTheAiIsUnavailable() {
        for (Reason reason : Reason.values()) {
            given(ai.generate(any(AiPurpose.class), any(AiRequest.class), any(UUID.class)))
                    .willThrow(new AiUnavailableException(reason, "x"));

            FeedbackResponse response = service.getOrCreate(user, session);

            assertThat(response.source()).as(reason.name()).isEqualTo(FeedbackSource.BASIC);
            assertThat(response.text()).contains("¡Muy buena sesión!", "Subiste de nivel en Atención");
        }
    }

    @Test
    void fallsBackWhenTheAiTextIsNotSuitable() {
        for (String bad : List.of("Tu capacidad de memoria mejoró un montón hoy.", "Bien.", "Mirá https://x.com para más datos.")) {
            aiSays(bad);
            assertThat(service.getOrCreate(user, session).source()).as(bad).isEqualTo(FeedbackSource.BASIC);
        }
    }

    @Test
    void returnsTheSavedFeedbackWithoutCallingTheAiAgain() {
        given(repository.findById(session)).willReturn(Optional.of(
                new SessionFeedback(session, "Texto guardado de la sesión.", FeedbackSource.AI, java.time.Instant.now())));

        FeedbackResponse response = service.getOrCreate(user, session);

        assertThat(response.text()).isEqualTo("Texto guardado de la sesión.");
        verifyNoInteractions(ai);
        verify(repository, never()).save(any());
    }

    @Test
    void anInvalidSessionNeverReachesTheAi() {
        given(sessions.result(user, session)).willThrow(new IllegalStateException("no es tuya"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getOrCreate(user, session))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(ai, repository);
    }

    @Test
    void twoSimultaneousRequestsEndWithTheFirstTextSaved() {
        given(ai.generate(any(AiPurpose.class), any(AiRequest.class), any(UUID.class)))
                .willThrow(new AiUnavailableException(Reason.DISABLED, "off"));
        given(repository.save(any())).willThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        given(repository.findById(session)).willReturn(Optional.empty(), Optional.of(
                new SessionFeedback(session, "El texto que ganó.", FeedbackSource.BASIC, java.time.Instant.now())));

        assertThat(service.getOrCreate(user, session).text()).isEqualTo("El texto que ganó.");
    }
}
