package com.appointment.services.persistence.custom.constants;

/**
 * Controls where the {@code %} wildcard is placed in a SQL {@code LIKE} expression.
 *
 * <p>Used with
 * {@link com.appointment.services.persistence.custom.builder.CriteriaBuilder#like(
 * com.appointment.services.persistence.custom.builder.Column, Object, LikeOperator)}
 * and
 * {@link com.appointment.services.persistence.custom.builder.CriteriaBuilder#notLike(
 * com.appointment.services.persistence.custom.builder.Column, Object, LikeOperator)}.
 *
 * <p><b>Examples</b> (value = {@code "john"}):
 * <ul>
 *   <li>{@link #ALL}   → {@code LIKE '%john%'} — matches anywhere in the string</li>
 *   <li>{@link #START} → {@code LIKE 'john%'}  — matches strings that start with "john"</li>
 *   <li>{@link #END}   → {@code LIKE '%john'}  — matches strings that end with "john"</li>
 * </ul>
 */
public enum LikeOperator {

    /**
     * Wraps the value with {@code %} on both sides: {@code %value%}.
     * Use for substring / contains searches.
     */
    ALL,

    /**
     * Appends {@code %} after the value: {@code value%}.
     * Use for prefix / starts-with searches.
     */
    START,

    /**
     * Prepends {@code %} before the value: {@code %value}.
     * Use for suffix / ends-with searches.
     */
    END
}
