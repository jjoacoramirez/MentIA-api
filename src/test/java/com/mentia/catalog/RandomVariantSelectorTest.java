package com.mentia.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.mentia.catalog.domain.ExerciseVariant;
import com.mentia.catalog.dto.VariantDefinition;

@ExtendWith(MockitoExtension.class)
class RandomVariantSelectorTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID EXERCISE = UUID.randomUUID();

    @Mock
    ExerciseVariantRepository repository;

    @Test
    void eligeSoloEntreLasVariantesActivasDelEjercicio() {
        given(repository.findByExerciseIdAndActiveTrueOrderByVariantKey(EXERCISE))
                .willReturn(List.of(variant("a"), variant("b"), variant("c")));
        RandomVariantSelector selector = new RandomVariantSelector(repository, new Random(7));

        Set<String> picked = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            VariantDefinition variant = selector.pick(USER, EXERCISE, 3);
            assertThat(variant.exerciseId()).isEqualTo(EXERCISE);
            picked.add(variant.variantKey());
        }

        assertThat(picked).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void conLaMismaSemillaEligeLoMismo() {
        given(repository.findByExerciseIdAndActiveTrueOrderByVariantKey(EXERCISE))
                .willReturn(List.of(variant("a"), variant("b"), variant("c")));

        List<String> first = picks(new RandomVariantSelector(repository, new Random(42)));
        List<String> second = picks(new RandomVariantSelector(repository, new Random(42)));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void unEjercicioSinVariantesActivasEsUnErrorDeContenido() {
        given(repository.findByExerciseIdAndActiveTrueOrderByVariantKey(EXERCISE)).willReturn(List.of());

        assertThatThrownBy(() -> new RandomVariantSelector(repository, new Random()).pick(USER, EXERCISE, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    private static List<String> picks(RandomVariantSelector selector) {
        return java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> selector.pick(USER, EXERCISE, 1).variantKey())
                .toList();
    }

    private static ExerciseVariant variant(String key) {
        try {
            var constructor = ExerciseVariant.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ExerciseVariant variant = constructor.newInstance();
            ReflectionTestUtils.setField(variant, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(variant, "exerciseId", EXERCISE);
            ReflectionTestUtils.setField(variant, "variantKey", key);
            ReflectionTestUtils.setField(variant, "content", Map.of("symbols", List.of("A", "B")));
            ReflectionTestUtils.setField(variant, "active", true);
            return variant;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
