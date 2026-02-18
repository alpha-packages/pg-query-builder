package com.appointment.services.persistence.custom.constants;

/**
 * Represents the sort direction used in SQL {@code ORDER BY} clauses.
 *
 * <p>Used with
 * {@link com.appointment.services.persistence.custom.builder.CriteriaBuilder#asc(
 * com.appointment.services.persistence.custom.builder.Column)} and
 * {@link com.appointment.services.persistence.custom.builder.CriteriaBuilder#desc(
 * com.appointment.services.persistence.custom.builder.Column)}.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * cb.customQueryBuilder()
 *     .select(cb.select(root))
 *     .orderBy(cb.asc(root.get("name")), cb.desc(root.get("createdAt")))
 *     .getQuery();
 * // → ... ORDER BY  m.name asc ,  m.created_at desc
 * }</pre>
 */
public enum OrderType {

    /** Ascending order — smallest value first (A → Z, 0 → 9, earliest → latest). */
    ASC("asc"),

    /** Descending order — largest value first (Z → A, 9 → 0, latest → earliest). */
    DESC("desc");

    /** The lowercase SQL keyword emitted into the ORDER BY clause. */
    private final String value;

    OrderType(String value) {
        this.value = value;
    }

    /**
     * Returns the lowercase SQL keyword for this sort direction ({@code "asc"} or {@code "desc"}).
     *
     * @return the SQL sort direction string
     */
    public String value() {
        return value;
    }

    /**
     * Returns the SQL keyword string, allowing this enum to be used directly
     * in string concatenation / {@link StringBuilder} appends without calling {@link #value()}.
     *
     * @return same as {@link #value()}
     */
    @Override
    public String toString() {
        return value;
    }
}
