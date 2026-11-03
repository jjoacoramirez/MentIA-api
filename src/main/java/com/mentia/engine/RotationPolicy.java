package com.mentia.engine;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Regla de rotación (docs/plan-backend.md, sección 7), igual para ejercicios dentro de una categoría y para
 * variantes dentro de un ejercicio:
 * <ul>
 *   <li>Lo que no se usó en las últimas {@code lookbackSessions} sesiones pesa 1.0.</li>
 *   <li>Lo usado hace {@code r} sesiones pesa {@code recencyWeights[r - 1]}.</li>
 *   <li>Con {@code avoidLastSession}, lo usado en la sesión anterior pesa 0 si hay alternativas.</li>
 *   <li>Se elige por sorteo ponderado. Si todo pesa 0, el sorteo es uniforme: siempre hay algo para elegir.</li>
 * </ul>
 * Función pura salvo por el {@link RandomGenerator}, que se inyecta para testear de forma determinística.
 */
public class RotationPolicy {

    private final RotationProperties properties;
    private final RandomGenerator random;

    public RotationPolicy(RotationProperties properties, RandomGenerator random) {
        this.properties = properties;
        this.random = random;
    }

    /**
     * @param sessionsAgo para cada id usado recientemente, hace cuántas sesiones fue su último uso (1 = la anterior)
     */
    public <T> T pick(List<T> candidates, Function<T, UUID> idOf, Map<UUID, Integer> sessionsAgo) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No hay candidatos para elegir");
        }
        double[] weights = weights(candidates, idOf, sessionsAgo);
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        if (total <= 0) {
            return candidates.get(random.nextInt(candidates.size()));
        }
        double target = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            target -= weights[i];
            if (target < 0) {
                return candidates.get(i);
            }
        }
        return candidates.getLast();
    }

    <T> double[] weights(List<T> candidates, Function<T, UUID> idOf, Map<UUID, Integer> sessionsAgo) {
        boolean hasAlternative = candidates.stream().anyMatch(c -> !usedInLastSession(idOf.apply(c), sessionsAgo));
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < weights.length; i++) {
            UUID id = idOf.apply(candidates.get(i));
            weights[i] = properties.avoidLastSession() && hasAlternative && usedInLastSession(id, sessionsAgo)
                    ? 0.0
                    : recencyWeight(sessionsAgo.get(id));
        }
        return weights;
    }

    double recencyWeight(Integer sessionsAgo) {
        if (sessionsAgo == null || sessionsAgo < 1 || sessionsAgo > properties.lookbackSessions()) {
            return 1.0;
        }
        return properties.recencyWeights().get(sessionsAgo - 1);
    }

    private static boolean usedInLastSession(UUID id, Map<UUID, Integer> sessionsAgo) {
        Integer ago = sessionsAgo.get(id);
        return ago != null && ago == 1;
    }
}
