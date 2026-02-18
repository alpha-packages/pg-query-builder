package com.appointment.services.persistence.custom.builder;

import lombok.Builder;

/**
 * Represents a single SQL column reference, optionally with an alias.
 *
 * <p>Instances are created via {@link Root#get(String)} or directly through
 * {@link CriteriaBuilder} helper methods such as {@code lower()}, {@code upper()},
 * {@code concat()}, and {@code count()}.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * Column idCol   = root.get("id");
 * Column nameCol = root.get("firstName", "first_name");  // with alias
 * }</pre>
 */
@Builder
public final class Column {

    /** Fully-qualified column expression, e.g. {@code m.first_name} or {@code LOWER(m.email)}. */
    private final String columnName;

    /**
     * Optional SQL alias applied in SELECT clauses (AS alias).
     * {@code null} when no alias is needed.
     */
    private final String alias;

    /**
     * Returns the column expression string used in SQL generation.
     *
     * @return the column name / expression
     */
    public String columnName() {
        return columnName;
    }

    /**
     * Returns the alias for this column, or {@code null} if none was set.
     *
     * @return alias string or {@code null}
     */
    public String alias() {
        return alias;
    }
}
