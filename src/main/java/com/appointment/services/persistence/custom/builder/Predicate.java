package com.appointment.services.persistence.custom.builder;

import lombok.Builder;
import lombok.Getter;

/**
 * Holds a single SQL condition fragment used in WHERE / JOIN ON clauses.
 *
 * <p>Predicates are created exclusively through {@link CriteriaBuilder} factory
 * methods (e.g. {@code equals}, {@code in}, {@code like}, {@code and}, {@code or})
 * and are never constructed directly.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * Predicate active  = cb.equals(root.get("active"), true);
 * Predicate deleted = cb.equals(root.get("deleted"), false);
 * Predicate combined = cb.and(active, deleted);
 * }</pre>
 */
@Builder
@Getter
public final class Predicate {

    /**
     * The raw SQL condition string, e.g. {@code  m.active = 'true' }.
     * Never {@code null}; may be blank for an empty predicate guard.
     */
    private final String condition;
}
