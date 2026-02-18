package com.appointment.services.persistence.custom.builder;

import com.appointment.services.persistence.custom.constants.JoinType;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.relational.core.mapping.Table;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a SQL table root (FROM or JOIN target) bound to a specific entity class.
 *
 * <p>A {@code Root<T>} is obtained from {@link CriteriaBuilder#from(Class)} or
 * {@link CriteriaBuilder#join(Class, Column, String, JoinType)} and is used to
 * derive type-safe column references via {@link #get(String)}.
 *
 * <p><b>Table name resolution:</b>
 * <ol>
 *   <li>If the class has {@code @Table("name")}, that value is used.</li>
 *   <li>Otherwise the class simple name is converted to snake_case.</li>
 * </ol>
 *
 * <p><b>Column name resolution:</b>
 * <ol>
 *   <li>If the field has {@code @Column("col")}, that value is used.</li>
 *   <li>Otherwise the field name is converted to snake_case.</li>
 *   <li>If not found on the class, the superclass is checked.</li>
 * </ol>
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * Root<Appointment> root = cb.from(Appointment.class);
 * Column idCol     = root.get("id");
 * Column nameAlias = root.get("firstName", "first_name");
 * Column allCols   = root.get();
 * }</pre>
 *
 * @param <T> the entity type this root is bound to
 */
@Getter
@Setter
@ToString
public class Root<T> {

    // -----------------------------------------------------------------------
    // Field-level cache: avoids repeated reflection lookups per (class, field)
    // -----------------------------------------------------------------------
    /** Shared cache: maps "ClassName.fieldName" → resolved SQL column name (without alias prefix). */
    private static final ConcurrentHashMap<String, String> COLUMN_NAME_CACHE = new ConcurrentHashMap<>(64);

    /** Shared cache: maps Class → resolved SQL table name. */
    private static final ConcurrentHashMap<Class<?>, String> TABLE_NAME_CACHE = new ConcurrentHashMap<>(32);

    // -----------------------------------------------------------------------
    // Instance state
    // -----------------------------------------------------------------------

    /** SQL alias assigned to this root (e.g. {@code m}, {@code j}, {@code j1}). */
    private String alias;

    /** Source column used on the left side of the JOIN ON clause. */
    private Column sourceColumn;

    /** Target column used on the right side of the JOIN ON clause. */
    private Column targetColumn;

    /** Join type (INNER, LEFT OUTER, etc.) when this root is a joined table. */
    private JoinType joinType;

    /** Optional extra predicate appended to the JOIN ON clause via AND. */
    private Predicate joinPredicates;

    /** The entity class this root is bound to. */
    private final Class<T> clazz;

    /**
     * Constructs a root bound to the given entity class.
     *
     * @param clazz the entity class; must not be {@code null}
     */
    public Root(Class<T> clazz) {
        this.clazz = clazz;
    }

    // -----------------------------------------------------------------------
    // Table name
    // -----------------------------------------------------------------------

    /**
     * Returns the SQL table name for this root's entity class.
     *
     * <p>Result is cached after the first call to avoid repeated annotation lookups.
     *
     * @return SQL table name string
     */
    public String getTable() {
        return TABLE_NAME_CACHE.computeIfAbsent(clazz, c -> {
            if (c.isAnnotationPresent(Table.class)) {
                String val = c.getAnnotation(Table.class).value();
                return val.isEmpty() ? toSnakeCase(c.getSimpleName()) : val;
            }
            return toSnakeCase(c.getSimpleName());
        });
    }

    // -----------------------------------------------------------------------
    // Column references
    // -----------------------------------------------------------------------

    /**
     * Returns a {@link Column} referencing all columns of this root ({@code alias.*}).
     *
     * @return wildcard column reference
     */
    public Column get() {
        return Column.builder().columnName(alias + ".*").build();
    }

    /**
     * Returns a {@link Column} for the given entity field name, resolved to its SQL column name.
     *
     * <p>Uses a shared cache to avoid repeated reflection per (class, fieldName) pair.
     *
     * @param attributeName the Java field name on the entity
     * @return column reference prefixed with this root's alias
     * @throws IllegalArgumentException if the field is not found on the class or its superclass
     */
    public Column get(String attributeName) {
        String cacheKey = clazz.getName() + '.' + attributeName;
        String colName = COLUMN_NAME_CACHE.computeIfAbsent(cacheKey, k -> resolveColumnName(attributeName));
        return Column.builder().columnName(alias + "." + colName).build();
    }

    /**
     * Returns a {@link Column} for the given entity field name with an SQL alias.
     *
     * @param attributeName the Java field name on the entity
     * @param alias         the SQL alias to apply in SELECT
     * @return column reference with alias
     */
    public Column get(String attributeName, @NotEmpty String alias) {
        return Column.builder()
                .columnName(get(attributeName).columnName())
                .alias(alias)
                .build();
    }

    /**
     * Returns a {@code COUNT(column)} expression for the given field.
     *
     * @param attributeName the Java field name on the entity
     * @return COUNT column expression
     */
    public Column count(String attributeName) {
        return Column.builder()
                .columnName(" COUNT (" + get(attributeName).columnName() + ") ")
                .build();
    }

    /**
     * Returns a {@code COUNT(column) AS alias} expression for the given field.
     *
     * @param attributeName the Java field name on the entity
     * @param alias         the SQL alias for the count result
     * @return COUNT column expression with alias
     */
    public Column count(String attributeName, @NotEmpty String alias) {
        return Column.builder()
                .columnName(" COUNT (" + get(attributeName).columnName() + ") ")
                .alias(alias)
                .build();
    }

    /**
     * Returns a {@code TO_CHAR(column, 'format')} expression for the given field.
     *
     * @param attributeName the Java field name on the entity
     * @param format        the PostgreSQL date/time format string (e.g. {@code "YYYY-MM-DD"})
     * @return TO_CHAR column expression
     */
    public Column toChar(String attributeName, @NotEmpty String format) {
        return Column.builder()
                .columnName(" TO_CHAR (" + get(attributeName).columnName() + " , '" + format + "') ")
                .build();
    }

    /**
     * Returns a {@code TO_CHAR(column, 'format') AS alias} expression for the given field.
     *
     * @param attributeName the Java field name on the entity
     * @param format        the PostgreSQL date/time format string
     * @param alias         the SQL alias for the result
     * @return TO_CHAR column expression with alias
     */
    public Column toChar(String attributeName, @NotEmpty String format, @NotEmpty String alias) {
        return Column.builder()
                .columnName(" TO_CHAR (" + get(attributeName).columnName() + " , '" + format + "') ")
                .alias(alias)
                .build();
    }

    /**
     * Attaches an extra predicate to this join's ON clause (appended with AND).
     *
     * @param predicate the additional join condition
     */
    public void joinConditions(Predicate predicate) {
        joinPredicates = predicate;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Resolves the SQL column name for a given Java field name via reflection.
     * Checks {@code @Column} annotation first, then falls back to snake_case conversion.
     */
    private String resolveColumnName(String attributeName) {
        Field field = findField(attributeName);
        if (field.isAnnotationPresent(org.springframework.data.relational.core.mapping.Column.class)) {
            String val = field.getAnnotation(org.springframework.data.relational.core.mapping.Column.class).value();
            return val.isEmpty() ? toSnakeCase(attributeName) : val;
        }
        return toSnakeCase(attributeName);
    }

    /**
     * Finds a declared field on this class or its immediate superclass.
     *
     * @param attributeName the field name to look up
     * @return the resolved {@link Field}
     * @throws IllegalArgumentException if the field is not found
     */
    private Field findField(String attributeName) {
        try {
            return clazz.getDeclaredField(attributeName);
        } catch (NoSuchFieldException e) {
            return getSuperClassField(attributeName);
        }
    }

    /**
     * Looks up a field on the immediate superclass.
     *
     * @param attributeName the field name to look up
     * @return the resolved {@link Field}
     * @throws IllegalArgumentException if the field is not found on the superclass
     */
    private Field getSuperClassField(String attributeName) {
        try {
            if (clazz.getSuperclass() == null) throw new NoSuchFieldException();
            return clazz.getSuperclass().getDeclaredField(attributeName);
        } catch (NoSuchFieldException | SecurityException ex) {
            throw new IllegalArgumentException("No Such Field : " + attributeName + " on " + clazz.getSimpleName());
        }
    }

    /**
     * Converts a camelCase name to snake_case.
     *
     * <p>Example: {@code "appointmentDate"} → {@code "appointment_date"}.
     *
     * @param name the camelCase string
     * @return the snake_case equivalent in lowercase
     */
    private static String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }
}
