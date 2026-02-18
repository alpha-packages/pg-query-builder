package com.appointment.services.persistence.custom.builder;

import com.appointment.services.persistence.custom.constants.JoinType;
import com.appointment.services.persistence.custom.constants.LikeOperator;
import com.appointment.services.persistence.custom.constants.OrderType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Entry point for building type-safe SQL queries for Spring Data R2DBC / JDBC.
 *
 * <p>A new {@code CriteriaBuilder} instance must be created per query — it is
 * <b>not thread-safe</b> and holds mutable state (the FROM root and JOIN roots).
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * CriteriaBuilder cb = new CriteriaBuilder();
 * Root<Order> root = cb.from(Order.class);
 * Root<Customer> customerRoot = cb.join(Customer.class, root.get("customerId"), "id", JoinType.LEFT_OUTER_JOIN);
 *
 * String sql = cb.customQueryBuilder()
 *     .select(cb.multiSelect(root.get("id"), customerRoot.get("name")))
 *     .where(cb.and(
 *         cb.equals(root.get("active"), true),
 *         cb.in(root.get("status"), List.of("OPEN", "PENDING"))
 *     ))
 *     .orderBy(cb.desc(root.get("createdAt")))
 *     .limit(20).offset(0)
 *     .getQuery();
 * }</pre>
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>Only one {@code from()} root is allowed per instance.</li>
 *   <li>Multiple {@code join()} roots are supported; aliases are auto-generated
 *       ({@code j}, {@code j1}, {@code j2}, …) to avoid collisions.</li>
 *   <li>Column name and table name resolution is cached inside {@link Root} via
 *       {@code ConcurrentHashMap} — reflection is only paid once per unique field.</li>
 * </ul>
 */
@Getter
@Slf4j
public class CriteriaBuilder {

    /** The primary FROM table root. Exactly one is allowed per builder. */
    private Root<?> fromRoot;

    /**
     * Ordered map of joined roots, keyed by their generated alias.
     * {@code LinkedHashMap} preserves insertion order so JOINs appear in the
     * same sequence they were declared.
     */
    private Map<String, Root<?>> joinRoot;

    // -----------------------------------------------------------------------
    // Query entry point
    // -----------------------------------------------------------------------

    /**
     * Creates a new {@link CustomQuery} pre-wired to this builder.
     *
     * <p>Call this method once per query; the returned {@code CustomQuery} is
     * a fluent builder that assembles the final SQL string via {@link CustomQuery#getQuery()}.
     *
     * @return a new {@link CustomQuery} bound to this {@code CriteriaBuilder}
     */
    public CustomQuery customQueryBuilder() {
        return new CustomQuery().criteriaBuilder(this);
    }

    // -----------------------------------------------------------------------
    // FROM / JOIN roots
    // -----------------------------------------------------------------------

    /**
     * Declares the primary FROM table for the query.
     *
     * <p>The alias {@code "m"} is assigned automatically. Only one FROM root
     * is allowed; calling this method twice throws {@link IllegalArgumentException}.
     *
     * @param <T>   the entity type
     * @param clazz the entity class (must have {@code @Table} or follow snake_case naming)
     * @return the {@link Root} representing the FROM table
     * @throws IllegalArgumentException if a FROM root has already been set
     */
    public <T> Root<T> from(Class<T> clazz) {
        if (fromRoot != null)
            throw new IllegalArgumentException("from class already defined");
        Root<T> root = new Root<>(clazz);
        root.setAlias("m");
        fromRoot = root;
        return root;
    }

    /**
     * Adds a JOIN to the query.
     *
     * <p>Aliases are auto-generated as {@code j}, {@code j1}, {@code j2}, … to
     * avoid collisions when joining the same table multiple times.
     *
     * @param <T>            the joined entity type
     * @param clazz          the joined entity class
     * @param sourceColumn   the column on the left side of the ON clause (from an existing root)
     * @param joinColumnName the field name on {@code clazz} for the right side of the ON clause
     * @param joinType       the SQL join type (INNER, LEFT OUTER, etc.)
     * @return the {@link Root} representing the joined table
     * @throws IllegalArgumentException if {@link #from(Class)} has not been called yet
     */
    public <T> Root<T> join(Class<T> clazz, Column sourceColumn, String joinColumnName, JoinType joinType) {
        if (fromRoot == null)
            throw new IllegalArgumentException("from not defined to join");
        if (joinRoot == null)
            joinRoot = new LinkedHashMap<>();

        Root<T> root = new Root<>(clazz);
        String alias = getUniqueKey(joinRoot, "j");
        root.setAlias(alias);
        root.setSourceColumn(sourceColumn);
        root.setTargetColumn(root.get(joinColumnName));
        root.setJoinType(joinType);
        joinRoot.put(alias, root);
        return root;
    }

    // -----------------------------------------------------------------------
    // SELECT clause builders
    // -----------------------------------------------------------------------

    /**
     * Produces {@code SELECT DISTINCT alias.* FROM} for the given root.
     *
     * @param selection the root whose wildcard columns to select
     * @return a {@link Select} fragment
     */
    public Select select(Root<?> selection) {
        return Select.builder()
                .data("SELECT DISTINCT " + selection.getAlias() + ".* FROM ")
                .build();
    }

    /**
     * Produces {@code SELECT COUNT(DISTINCT alias.*) FROM} for the given root.
     *
     * @param selection the root to count
     * @return a {@link Select} fragment
     */
    public Select count(Root<?> selection) {
        return Select.builder()
                .data("SELECT COUNT(DISTINCT " + selection.getAlias() + ".*) FROM ")
                .build();
    }

    /**
     * Produces {@code SELECT COUNT(DISTINCT col1, col2, ...) FROM} for the given columns.
     *
     * @param selections one or more columns to count
     * @return a {@link Select} fragment
     */
    public Select count(Column... selections) {
        return Select.builder()
                .data("SELECT COUNT(DISTINCT " + joinColumnNames(selections) + ") FROM ")
                .build();
    }

    /**
     * Produces {@code SELECT DISTINCT col1, col2 [AS alias], ... FROM} for the given columns.
     *
     * @param selections one or more columns to include in the SELECT list
     * @return a {@link Select} fragment
     */
    public Select multiSelect(Column... selections) {
        return Select.builder()
                .data("SELECT DISTINCT " + buildSelectList(selections) + " FROM ")
                .build();
    }

    /**
     * Produces a {@code DISTINCT ON (col1, col2, ...)} fragment for PostgreSQL queries.
     *
     * @param distinctOn the columns to apply DISTINCT ON
     * @return a {@link Distinct} fragment
     */
    public Distinct selectDistinctOn(Column... distinctOn) {
        return Distinct.builder()
                .data("DISTINCT ON (" + joinColumnNames(distinctOn) + ")")
                .build();
    }

    // -----------------------------------------------------------------------
    // Predicate builders
    // -----------------------------------------------------------------------

    /**
     * Creates an equality predicate: {@code column = 'value'}.
     *
     * @param column the column to test
     * @param value  the value to compare against
     * @return an equality {@link Predicate}
     */
    public Predicate equals(Column column, Object value) {
        return Predicate.builder()
                .condition(" " + column.columnName() + " = '" + value + "' ")
                .build();
    }

    /**
     * Creates an IN predicate: {@code column IN ('v1','v2',...)}.
     *
     * @param column the column to test
     * @param values the list of allowed values
     * @return an IN {@link Predicate}
     */
    public Predicate in(Column column, List<?> values) {
        return Predicate.builder()
                .condition(" " + column.columnName() + " IN ('" + joinValues(values) + "') ")
                .build();
    }

    /**
     * Creates a NOT IN predicate: {@code column NOT IN ('v1','v2',...)}.
     *
     * @param column the column to test
     * @param values the list of excluded values
     * @return a NOT IN {@link Predicate}
     */
    public Predicate notIn(Column column, List<?> values) {
        return Predicate.builder()
                .condition(" " + column.columnName() + " NOT IN ('" + joinValues(values) + "') ")
                .build();
    }

    /**
     * Creates a LIKE predicate with the specified wildcard placement.
     *
     * <p>Use {@link LikeOperator#ALL} for {@code %value%},
     * {@link LikeOperator#START} for {@code value%},
     * {@link LikeOperator#END} for {@code %value}.
     *
     * @param column       the column to test
     * @param value        the search term
     * @param likeOperator the wildcard placement strategy
     * @return a LIKE {@link Predicate}
     */
    public Predicate like(Column column, Object value, LikeOperator likeOperator) {
        return Predicate.builder()
                .condition(" " + column.columnName() + " LIKE '" + applyLikeWildcard(likeOperator, value) + "' ")
                .build();
    }

    /**
     * Creates a NOT LIKE predicate with the specified wildcard placement.
     *
     * @param column       the column to test
     * @param value        the search term
     * @param likeOperator the wildcard placement strategy
     * @return a NOT LIKE {@link Predicate}
     */
    public Predicate notLike(Column column, Object value, LikeOperator likeOperator) {
        return Predicate.builder()
                .condition(" " + column.columnName() + " NOT LIKE '" + applyLikeWildcard(likeOperator, value) + "' ")
                .build();
    }

    /**
     * Creates a BETWEEN predicate: {@code column BETWEEN 'value1' AND 'value2'}.
     *
     * @param column the column to test
     * @param value1 the lower bound (inclusive)
     * @param value2 the upper bound (inclusive)
     * @return a BETWEEN {@link Predicate}
     */
    public Predicate between(Column column, Object value1, Object value2) {
        return Predicate.builder()
                .condition(" " + column.columnName() + " BETWEEN '" + value1 + "' and '" + value2 + "' ")
                .build();
    }

    /**
     * Creates a greater-than predicate: {@code column > 'value'}.
     *
     * @param column the column to test
     * @param value  the threshold value
     * @return a {@link Predicate}
     */
    public Predicate greaterThan(Column column, Object value) {
        return Predicate.builder()
                .condition(" " + column.columnName() + " > '" + value + "' ")
                .build();
    }

    /**
     * Creates a less-than predicate: {@code column < 'value'}.
     *
     * @param column the column to test
     * @param value  the threshold value
     * @return a {@link Predicate}
     */
    public Predicate lessThan(Column column, Object value) {
        return Predicate.builder()
                .condition(" " + column.columnName() + " < '" + value + "' ")
                .build();
    }

    /**
     * Creates a greater-than-or-equal predicate: {@code column >= 'value'}.
     *
     * @param column the column to test
     * @param value  the threshold value
     * @return a {@link Predicate}
     */
    public Predicate greaterThanEqual(Column column, Object value) {
        return Predicate.builder()
                .condition(" " + column.columnName() + " >= '" + value + "' ")
                .build();
    }

    /**
     * Creates a less-than-or-equal predicate: {@code column <= 'value'}.
     *
     * @param column the column to test
     * @param value  the threshold value
     * @return a {@link Predicate}
     */
    public Predicate lessThanEqual(Column column, Object value) {
        return Predicate.builder()
                .condition(" " + column.columnName() + " <= '" + value + "' ")
                .build();
    }

    /**
     * Creates an IS NULL predicate: {@code column IS NULL}.
     *
     * @param column the column to test
     * @return an IS NULL {@link Predicate}
     */
    public Predicate isNull(Column column) {
        return Predicate.builder()
                .condition(column.columnName() + " IS NULL ")
                .build();
    }

    /**
     * Creates an IS NOT NULL predicate: {@code column IS NOT NULL}.
     *
     * @param column the column to test
     * @return an IS NOT NULL {@link Predicate}
     */
    public Predicate isNotNull(Column column) {
        return Predicate.builder()
                .condition(column.columnName() + " IS NOT NULL ")
                .build();
    }

    // -----------------------------------------------------------------------
    // Logical combinators
    // -----------------------------------------------------------------------

    /**
     * Combines multiple predicates with AND: {@code (p1 AND p2 AND ...)}.
     *
     * @param predicates two or more predicates to combine
     * @return a compound AND {@link Predicate}
     * @throws IllegalArgumentException if no predicates are provided
     */
    public Predicate and(Predicate... predicates) {
        if (predicates.length == 0)
            throw new IllegalArgumentException("Predicates is Empty in AND");
        return Predicate.builder()
                .condition(joinPredicates(" and ", predicates))
                .build();
    }

    /**
     * Combines multiple predicates with OR: {@code (p1 OR p2 OR ...)}.
     *
     * @param predicates two or more predicates to combine
     * @return a compound OR {@link Predicate}
     * @throws IllegalArgumentException if no predicates are provided
     */
    public Predicate or(Predicate... predicates) {
        if (predicates.length == 0)
            throw new IllegalArgumentException("Predicates is Empty in OR");
        return Predicate.builder()
                .condition(joinPredicates(" or ", predicates))
                .build();
    }

    // -----------------------------------------------------------------------
    // Column expression helpers
    // -----------------------------------------------------------------------

    /**
     * Wraps a column in a {@code LOWER()} SQL function.
     *
     * @param column the column to wrap
     * @return a new {@link Column} with the LOWER expression
     */
    public Column lower(Column column) {
        return Column.builder().columnName("LOWER(" + column.columnName() + ")").build();
    }

    /**
     * Wraps a column in an {@code UPPER()} SQL function.
     *
     * @param column the column to wrap
     * @return a new {@link Column} with the UPPER expression
     */
    public Column upper(Column column) {
        return Column.builder().columnName("UPPER(" + column.columnName() + ")").build();
    }

    /**
     * Builds a {@code CONCAT(a || b || ...)} expression from a mix of {@link Column}
     * and {@link String} arguments.
     *
     * <p>String literals are automatically wrapped in single quotes.
     *
     * @param columns a varargs mix of {@link Column} and {@link String} values
     * @return a new {@link Column} with the CONCAT expression
     * @throws IllegalArgumentException if any argument is neither a {@link Column} nor a {@link String}
     */
    public Column concat(Object... columns) {
        return Column.builder().columnName("CONCAT(" + buildConcatExpression(columns) + ")").build();
    }

    // -----------------------------------------------------------------------
    // ORDER BY helpers
    // -----------------------------------------------------------------------

    /**
     * Creates an ascending {@link Order} for the given column.
     *
     * @param column the column to sort by
     * @return an ASC {@link Order}
     */
    public Order asc(Column column) {
        return Order.builder().columnName(column).orderType(OrderType.ASC).build();
    }

    /**
     * Creates a descending {@link Order} for the given column.
     *
     * @param column the column to sort by
     * @return a DESC {@link Order}
     */
    public Order desc(Column column) {
        return Order.builder().columnName(column).orderType(OrderType.DESC).build();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Generates a unique alias key by appending an incrementing suffix if the base key
     * already exists in the map (e.g. {@code j} → {@code j1} → {@code j2}).
     *
     * @param map     the existing alias map to check against
     * @param baseKey the preferred base alias (e.g. {@code "j"})
     * @return a unique alias string not present in {@code map}
     */
    public String getUniqueKey(Map<String, Root<?>> map, String baseKey) {
        if (!map.containsKey(baseKey)) return baseKey;
        int suffix = 1;
        String key;
        do { key = baseKey + suffix++; } while (map.containsKey(key));
        return key;
    }

    /** Joins column names with {@code ", "} for use in SELECT / COUNT / DISTINCT ON. */
    private String joinColumnNames(Column... columns) {
        return Arrays.stream(columns)
                .map(Column::columnName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Builds the SELECT column list, appending {@code AS alias} where an alias is set.
     * Uses {@link StringBuilder} to avoid intermediate string allocations.
     */
    private String buildSelectList(Column... columns) {
        StringBuilder sb = new StringBuilder(columns.length * 24);
        for (int i = 0; i < columns.length; i++) {
            Column c = columns[i];
            sb.append(c.columnName());
            if (c.alias() != null) sb.append(" AS ").append(c.alias()).append(' ');
            if (i < columns.length - 1) sb.append(", ");
        }
        return sb.toString();
    }

    /** Joins list values with {@code ','} for IN / NOT IN clauses. */
    private String joinValues(List<?> values) {
        // Pre-size the StringBuilder based on estimated value length
        StringBuilder sb = new StringBuilder(values.size() * 8);
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append("','");
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    /** Applies the wildcard pattern to a LIKE value based on the operator. */
    private Object applyLikeWildcard(LikeOperator likeOperator, Object value) {
        if (likeOperator == null) return value;
        return switch (likeOperator) {
            case ALL   -> "%" + value + "%";
            case START -> value + "%";
            case END   -> "%" + value;
        };
    }

    /** Wraps predicate conditions in parentheses joined by the given logical operator. */
    private String joinPredicates(String operator, Predicate... predicates) {
        StringBuilder sb = new StringBuilder(" (");
        for (int i = 0; i < predicates.length; i++) {
            if (i > 0) sb.append(operator);
            sb.append(predicates[i].getCondition());
        }
        sb.append(") ");
        return sb.toString();
    }

    /**
     * Builds the {@code a || b || ...} expression for CONCAT.
     * Columns are used as-is; Strings are wrapped in single quotes.
     */
    private String buildConcatExpression(Object... parts) {
        StringBuilder sb = new StringBuilder(parts.length * 12);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(" || ");
            Object part = parts[i];
            if (part instanceof Column col) {
                sb.append(col.columnName());
            } else if (part instanceof String str) {
                sb.append('\'').append(str).append('\'');
            } else {
                throw new IllegalArgumentException("Only Column or String can be the input to concat()");
            }
        }
        return sb.toString();
    }
}
