package com.appointment.services.persistence.custom.builder;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Carries the SELECT clause fragment produced by {@link CriteriaBuilder}
 * methods such as {@code select()}, {@code multiSelect()}, and {@code count()}.
 *
 * <p>This is an internal transfer object; consumers should not build it directly.
 *
 * <p><b>Example output stored in {@code data}:</b>
 * <pre>
 *   "SELECT DISTINCT m.* FROM "
 *   "SELECT DISTINCT m.id, m.name FROM "
 *   "SELECT COUNT(DISTINCT m.*) FROM "
 * </pre>
 */
@Getter
@Setter
@Builder
public final class Select {

    /**
     * The partial SELECT … FROM clause string.
     * The FROM table/alias is appended by {@link CustomQuery} during {@code getQuery()}.
     */
    private String data;
}
