package com.mentia.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mentia.catalog.CatalogService;
import com.mentia.catalog.dto.VariantDefinition;
import com.mentia.session.SessionHistoryService;
import com.mentia.session.dto.RecentUsage;

@ExtendWith(MockitoExtension.class)
class RotationVariantSelectorTest {

    private static final RotationProperties PROPERTIES =
            new RotationProperties(5, true, List.of(0.0, 0.25, 0.5, 0.75, 1.0));
    private static final UUID USER = UUID.randomUUID();
    private static final UUID EXERCISE = UUID.randomUUID();

    @Mock
    CatalogService catalogService;

    @Mock
    SessionHistoryService historyService;

    @Test
    void noRepiteLaVarianteDeLaSesionAnterior() {
        VariantDefinition last = variant("letras");
        VariantDefinition other = variant("figuras");
        given(catalogService.activeVariants(EXERCISE)).willReturn(List.of(last, other));
        given(historyService.recentUsage(USER, 5)).willReturn(new RecentUsage(Map.of(), Map.of(last.id(), 1)));

        RotationVariantSelector selector = new RotationVariantSelector(catalogService, historyService,
                new RotationPolicy(PROPERTIES, new Random(3)), PROPERTIES);

        for (int i = 0; i < 100; i++) {
            assertThat(selector.pick(USER, EXERCISE, 4)).isEqualTo(other);
        }
    }

    @Test
    void unEjercicioSinVariantesActivasEsUnErrorDeContenido() {
        given(catalogService.activeVariants(EXERCISE)).willReturn(List.of());

        RotationVariantSelector selector = new RotationVariantSelector(catalogService, historyService,
                new RotationPolicy(PROPERTIES, new Random()), PROPERTIES);

        assertThatThrownBy(() -> selector.pick(USER, EXERCISE, 1)).isInstanceOf(IllegalStateException.class);
    }

    private static VariantDefinition variant(String key) {
        return new VariantDefinition(UUID.randomUUID(), EXERCISE, key, Map.of("symbols", List.of("A")));
    }
}
