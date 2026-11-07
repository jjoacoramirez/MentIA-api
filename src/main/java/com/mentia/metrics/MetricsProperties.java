package com.mentia.metrics;

import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * {@code mentia.metrics.*}: métricas internas para admins.
 *
 * @param timeZone          zona en la que se cuentan los días activos (la misma que el progreso)
 * @param consistencyGroups piso de días activos de cada grupo de constancia, en orden creciente y desde 1
 *                          (por ejemplo {@code [1, 2, 4, 8]}: 1, 2 a 3, 4 a 7 y 8 o más). Los usuarios sin sesiones
 *                          completadas forman un grupo aparte.
 * @param includeAdmins     si las cuentas admin (de prueba) entran en las métricas
 */
@Validated
@ConfigurationProperties(prefix = "mentia.metrics")
public record MetricsProperties(
        @NotNull ZoneId timeZone,
        @NotEmpty List<Integer> consistencyGroups,
        boolean includeAdmins) {

    @AssertTrue(message = "consistency-groups tiene que empezar en 1 y ser estrictamente creciente")
    public boolean isGroupsOrdered() {
        if (consistencyGroups == null || consistencyGroups.isEmpty() || consistencyGroups.getFirst() != 1) {
            return false;
        }
        for (int i = 1; i < consistencyGroups.size(); i++) {
            if (consistencyGroups.get(i) <= consistencyGroups.get(i - 1)) {
                return false;
            }
        }
        return true;
    }
}
