package com.appointment.services.persistence.custom.constants;

/**
 * Enumerates the SQL JOIN types supported by {@link com.appointment.services.persistence.custom.builder.CriteriaBuilder}.
 *
 * <p>Pass a value to
 * {@link com.appointment.services.persistence.custom.builder.CriteriaBuilder#join(Class,
 * com.appointment.services.persistence.custom.builder.Column, String, JoinType)}
 * to control how the joined table is included in the result set.
 *
 * <table border="1">
 *   <caption>Join type behaviour</caption>
 *   <tr><th>Constant</th><th>SQL</th><th>Behaviour</th></tr>
 *   <tr><td>{@link #JOIN}</td><td>{@code JOIN}</td><td>Returns only rows with a match in both tables (INNER JOIN).</td></tr>
 *   <tr><td>{@link #CROSS_JOIN}</td><td>{@code CROSS JOIN}</td><td>Cartesian product — every combination of rows from both tables.</td></tr>
 *   <tr><td>{@link #LEFT_OUTER_JOIN}</td><td>{@code LEFT OUTER JOIN}</td><td>All rows from the left table; {@code NULL} for unmatched right rows.</td></tr>
 *   <tr><td>{@link #RIGHT_OUTER_JOIN}</td><td>{@code RIGHT OUTER JOIN}</td><td>All rows from the right table; {@code NULL} for unmatched left rows.</td></tr>
 *   <tr><td>{@link #FULL_OUTER_JOIN}</td><td>{@code FULL OUTER JOIN}</td><td>All rows from both tables; {@code NULL} where there is no match.</td></tr>
 * </table>
 */
public enum JoinType {

    /** Standard INNER JOIN — only matching rows from both tables. */
    JOIN("JOIN"),

    /** Cartesian product of both tables (no ON condition needed). */
    CROSS_JOIN("CROSS JOIN"),

    /** All rows from the left (FROM) table; NULLs for unmatched right rows. */
    LEFT_OUTER_JOIN("LEFT OUTER JOIN"),

    /** All rows from the right (JOIN) table; NULLs for unmatched left rows. */
    RIGHT_OUTER_JOIN("RIGHT OUTER JOIN"),

    /** All rows from both tables; NULLs where there is no match on either side. */
    FULL_OUTER_JOIN("FULL OUTER JOIN");

    /** The exact SQL keyword(s) emitted into the query string. */
    private final String sql;

    JoinType(String sql) {
        this.sql = sql;
    }

    /**
     * Returns the SQL keyword(s) for this join type, e.g. {@code "LEFT OUTER JOIN"}.
     *
     * @return the SQL string
     */
    public String sql() {
        return sql;
    }
}
