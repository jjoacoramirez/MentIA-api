package com.mentia.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.mentia.catalog.domain.Category;
import com.mentia.catalog.domain.Exercise;
import com.mentia.catalog.domain.ExerciseType;
import com.mentia.catalog.domain.Modality;
import com.mentia.catalog.dto.CategoryResponse;
import com.mentia.catalog.dto.ExerciseDefinition;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    CategoryRepository categoryRepository;

    @Mock
    ExerciseRepository exerciseRepository;

    @Mock
    ExerciseVariantRepository variantRepository;

    @Mock
    ExerciseLevelRepository levelRepository;

    @Test
    void devuelveCategoriasActivasComoDto() {
        given(categoryRepository.findByActiveTrueOrderByMvpDescNameAsc())
                .willReturn(List.of(category("ATENCION", "Atención", true), category("MEMORIA_VISUAL", "Memoria visual", true)));

        CatalogService service = new CatalogService(categoryRepository, exerciseRepository, variantRepository, levelRepository);

        assertThat(service.activeCategories()).containsExactly(
                new CategoryResponse("ATENCION", "Atención", "desc", true),
                new CategoryResponse("MEMORIA_VISUAL", "Memoria visual", "desc", true));
        assertThat(service.activeCategoryCodes()).containsExactly("ATENCION", "MEMORIA_VISUAL");
    }

    @Test
    void devuelveEjerciciosActivosDeLaCategoria() {
        Category attention = category("ATENCION", "Atención", true);
        Exercise stroop = exercise("STROOP", ExerciseType.STROOP, attention);
        given(exerciseRepository.findActiveByCategoryCode("ATENCION")).willReturn(List.of(stroop));

        List<ExerciseDefinition> result = new CatalogService(categoryRepository, exerciseRepository, variantRepository, levelRepository)
                .activeExercises("ATENCION");

        assertThat(result).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo(stroop.getId());
            assertThat(e.type()).isEqualTo(ExerciseType.STROOP);
            assertThat(e.categoryCode()).isEqualTo("ATENCION");
            assertThat(e.modality()).isEqualTo(Modality.VISUAL);
        });
    }

    static Category category(String code, String name, boolean mvp) {
        Category category = instantiate(Category.class);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(category, "code", code);
        ReflectionTestUtils.setField(category, "name", name);
        ReflectionTestUtils.setField(category, "description", "desc");
        ReflectionTestUtils.setField(category, "mvp", mvp);
        ReflectionTestUtils.setField(category, "active", true);
        return category;
    }

    static Exercise exercise(String code, ExerciseType type, Category category) {
        Exercise exercise = instantiate(Exercise.class);
        ReflectionTestUtils.setField(exercise, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(exercise, "code", code);
        ReflectionTestUtils.setField(exercise, "name", code);
        ReflectionTestUtils.setField(exercise, "type", type);
        ReflectionTestUtils.setField(exercise, "category", category);
        ReflectionTestUtils.setField(exercise, "modality", Modality.VISUAL);
        ReflectionTestUtils.setField(exercise, "instructions", "Instrucciones.");
        ReflectionTestUtils.setField(exercise, "active", true);
        return exercise;
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
