package com.appointment.services.persistence.custom.builder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fluent SQL query assembler that combines the parts produced by {@link CriteriaBuilder}
 * into a final SQL string via {@link #getQuery()}.
 *
 * <p>Obtain an instance from {@link CriteriaBuilder#customQueryBuilder()} — do not
 * instantiate directly. Each method returns {@code this} for chaining.
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * String sql = cb.customQueryBuilder()
 *     .select(cb.multiSelect(root.get("id"), root.get("name")))
 *     .where(cb.and(cb.equals(root.get("active"), true)))
 *     .orderBy(cb.desc(root.get("createdAt")))
 *     .limit(20).offset(0L)
 *     .getQuery();
 * }</pre>
 *
 * <h2>Constraints</h2>
 * <ul>
 *   <li>{@code DISTINCT} and {@code DISTINCT ON} are mutually exclusive.</li>
 *   <li>{@code offset()} requires {@code limit()} to be set (limit &gt; 0).</li>
 *   <li>{@code where()} rejects a blank condition string.</li>
 * </ul>
 */
public class CustomQuery {

    /** The SELECT … FROM fragment. */
    private Select select;

    /** The CriteriaBuilder that owns the FROM / JOIN roots. */
    private CriteriaBuilder criteriaBuilder;

    /** The WHERE predicate. {@code null} means no WHERE clause. */
    private Predicate where;

    /** The DISTINCT ON fragment (PostgreSQL-specific). {@code null} means not used. */
    private Distinct distinctOn;

    /** The ORDER BY clause string, pre-assembled from {@link Order} objects. */
    private String orderBy;

    /** LIMIT value; 0 means no LIMIT. */
    private int limit;

    /** OFFSET value; {@code null} means no OFFSET. */
    private Long offset;

    /**
     * When {@code true}, the DISTINCT keyword is kept in the SELECT clause.
     * When {@code false}, it is stripped. Default is neither set ({@code false}).
     */
    private boolean distinct;

    /** The GROUP BY clause string, pre-assembled from column names. */
    private String groupBy;

    // -----------------------------------------------------------------------
    // Fluent setters
    // -----------------------------------------------------------------------

    /**
     * Sets the SELECT clause.
     *
     * @param selection the SELECT fragment from {@link CriteriaBuilder#select},
     *                  {@link CriteriaBuilder#multiSelect}, or {@link CriteriaBuilder#count}
     * @return {@code this} for chaining
     */
    public CustomQuery select(Select selection) {
        select = selection;
        return this;
    }

    /**
     * Wires this query to its owning {@link CriteriaBuilder} (called internally).
     *
     * @param builder the owning builder
     * @return {@code this} for chaining
     */
    public CustomQuery criteriaBuilder(CriteriaBuilder builder) {
        criteriaBuilder = builder;
        return this;
    }

    /**
     * Sets the WHERE clause predicate.
     *
     * @param predicate the condition to filter rows; must not be blank
     * @return {@code this} for chaining
     * @throws IllegalArgumentException if the predicate condition string is blank
     */
    public CustomQuery where(Predicate predicate) {
        if (predicate.getCondition().isBlank())
            throw new IllegalArgumentException("Predicates is Empty in Where");
        where = predicate;
        return this;
    }

    /**
     * Sets the {@code DISTINCT ON (...)} clause (PostgreSQL-specific).
     *
     * <p>Mutually exclusive with {@link #distinct(boolean) distinct(true)}.
     *
     * @param distinct the DISTINCT ON fragment
     * @return {@code this} for chaining
     */
    public CustomQuery distinctOn(Distinct distinct) {
        distinctOn = distinct;
        return this;
    }

    /**
     * Sets the ORDER BY clause from one or more {@link Order} terms.
     *
     * @param orders the sort terms in priority order
     * @return {@code this} for chaining
     */
    public CustomQuery orderBy(Order... orders) {
        orderBy = buildOrderClause(orders);
        return this;
    }

    /**
     * Sets the ORDER BY clause from a list of {@link Order} terms.
     *
     * @param orders the sort terms in priority order
     * @return {@code this} for chaining
     */
    public CustomQuery orderBy(List<Order> orders) {
        orderBy = buildOrderClause(orders.toArray(Order[]::new));
        return this;
    }

    /**
     * Sets the LIMIT value.
     *
     * @param limitValue the maximum number of rows to return; must be &gt; 0 to take effect
     * @return {@code this} for chaining
     */
    public CustomQuery limit(int limitValue) {
        limit = limitValue;
        return this;
    }

    /**
     * Sets the OFFSET value.
     *
     * <p>Requires {@link #limit(int)} to be set to a value &gt; 0.
     *
     * @param offsetValue the number of rows to skip
     * @return {@code this} for chaining
     */
    public CustomQuery offset(long offsetValue) {
        offset = offsetValue;
        return this;
    }

    /**
     * Controls whether the {@code DISTINCT} keyword is kept ({@code true}) or
     * stripped ({@code false}) from the SELECT clause.
     *
     * <p>Mutually exclusive with {@link #distinctOn(Distinct)}.
     *
     * @param isDistinct {@code true} to keep DISTINCT, {@code false} to remove it
     * @return {@code this} for chaining
     */
    public CustomQuery distinct(boolean isDistinct) {
        distinct = isDistinct;
        return this;
    }

    /**
     * Sets the GROUP BY clause from one or more columns.
     *
     * @param columns the columns to group by
     * @return {@code this} for chaining
     */
    public CustomQuery groupBy(Column... columns) {
        StringBuilder sb = new StringBuilder(columns.length * 16);
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) sb.append(" ,");
            sb.append(columns[i].columnName());
        }
        groupBy = sb.toString();
        return this;
    }

    // -----------------------------------------------------------------------
    // Query assembly
    // -----------------------------------------------------------------------

    /**
     * Assembles and returns the complete SQL query string.
     *
     * <p>The query is built by concatenating:
     * <ol>
     *   <li>SELECT clause (with DISTINCT / DISTINCT ON handling)</li>
     *   <li>FROM table + alias</li>
     *   <li>JOIN clauses (in insertion order)</li>
     *   <li>WHERE clause (if set)</li>
     *   <li>GROUP BY clause (if set)</li>
     *   <li>ORDER BY clause (if set)</li>
     *   <li>LIMIT clause (if limit &gt; 0)</li>
     *   <li>OFFSET clause (if set)</li>
     * </ol>
     *
     * @return the complete SQL string ready to pass to {@code DatabaseClient.sql()}
     * @throws IllegalArgumentException if offset is set without a limit, or if
     *                                  DISTINCT and DISTINCT ON are both active
     */
    public String getQuery() {
        // Pre-size: typical query is 200–600 chars
        StringBuilder sb = new StringBuilder(256);
        sb.append(buildSelectClause())
          .append(buildFromClause())
          .append(buildJoinClause())
          .append(buildWhereClause())
          .append(buildGroupByClause())
          .append(buildOrderByClause())
          .append(buildLimitClause())
          .append(buildOffsetClause());
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Private clause builders
    // -----------------------------------------------------------------------

    /**
     * Resolves the SELECT clause, handling DISTINCT / DISTINCT ON / plain SELECT.
     *
     * @throws IllegalArgumentException if both {@code distinct=true} and a DISTINCT ON are set
     */
    private String buildSelectClause() {
        String distinctOnData = distinctOn != null ? distinctOn.getData() : "";
        if (distinct && !distinctOnData.isBlank())
            throw new IllegalArgumentException("either DISTINCT or DISTINCT ON can be used");
        if (!distinct && distinctOnData.isBlank())
            // Strip the DISTINCT keyword — caller explicitly asked for non-distinct
            return select.getData().replace("DISTINCT ", "");
        if (!distinctOnData.isBlank())
            // Replace the DISTINCT keyword with DISTINCT ON (...)
            return select.getData().replace("DISTINCT", distinctOnData);
        // distinct=true, no DISTINCT ON → keep SELECT DISTINCT as-is
        return select.getData();
    }

    /** Returns {@code "tableName alias"} for the FROM clause. */
    private String buildFromClause() {
        nullCheck();
        Root<?> from = criteriaBuilder.getFromRoot();
        return from.getTable() + " " + from.getAlias();
    }

    /**
     * Builds all JOIN clauses in insertion order.
     * Each join is: {@code JOIN_TYPE table alias ON sourceCol = targetCol [AND extraCondition]}.
     */
    private String buildJoinClause() {
        nullCheck();
        Map<String, Root<?>> joins = criteriaBuilder.getJoinRoot();
        if (joins == null || joins.isEmpty()) return "";

        StringBuilder sb = new StringBuilder(joins.size() * 80);
        for (Root<?> root : joins.values()) {
            sb.append(' ')
              .append(root.getJoinType().sql())
              .append(' ')
              .append(root.getTable())
              .append(' ')
              .append(root.getAlias())
              .append(" ON ")
              .append(root.getSourceColumn().columnName())
              .append(" = ")
              .append(root.getTargetColumn().columnName());

            Optional.ofNullable(root.getJoinPredicates())
                    .ifPresent(p -> sb.append(" AND ").append(p.getCondition()));
            sb.append(' ');
        }
        return sb.toString();
    }

    /** Returns the WHERE clause string, or empty string if no predicate is set. */
    private String buildWhereClause() {
        if (where == null) return "";
        String condition = where.getCondition();
        return condition.isBlank() ? "" : " WHERE " + condition;
    }

    /** Returns the LIMIT clause string, or empty string if limit is not set. */
    private String buildLimitClause() {
        return limit > 0 ? " LIMIT " + limit : "";
    }

    /** Returns the ORDER BY clause string, or empty string if not set. */
    private String buildOrderByClause() {
        return (orderBy != null && !orderBy.isBlank()) ? " ORDER BY " + orderBy : "";
    }

    /** Returns the GROUP BY clause string, or empty string if not set. */
    private String buildGroupByClause() {
        return (groupBy != null && !groupBy.isBlank()) ? " GROUP BY " + groupBy : "";
    }

    /**
     * Returns the OFFSET clause string, or empty string if not set.
     *
     * @throws IllegalArgumentException if offset is set but limit is 0
     */
    private String buildOffsetClause() {
        if (offset == null) return "";
        if (limit < 1)
            throw new IllegalArgumentException("limit should not be less than 1 with offset value");
        return " OFFSET " + offset;
    }

    /** Validates that the CriteriaBuilder is wired before accessing roots. */
    private void nullCheck() {
        if (criteriaBuilder == null)
            throw new IllegalArgumentException("criteriaBuilder is null");
    }

    /**
     * Builds the ORDER BY string from an array of {@link Order} terms.
     * Uses {@link StringBuilder} to avoid stream overhead.
     */
    private String buildOrderClause(Order[] orders) {
        StringBuilder sb = new StringBuilder(orders.length * 20);
        for (int i = 0; i < orders.length; i++) {
            if (i > 0) sb.append(',');
            Order o = orders[i];
            sb.append(' ').append(o.getColumnName().columnName()).append(' ').append(o.getOrderType());
        }
        return sb.toString();
    }
}
