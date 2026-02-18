# Custom Query Builder

[![Maven Central](https://img.shields.io/maven-central/v/io.github.YOUR_GITHUB_USERNAME/custom-query-builder.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.YOUR_GITHUB_USERNAME/custom-query-builder)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/projects/jdk/17/)

A fluent, type-safe SQL query builder for **Spring Data R2DBC / JDBC**.  
Compose complex SQL queries programmatically — no raw SQL strings, no JPA overhead.

---

## Features

- ✅ Type-safe column references via `Root<T>.get("fieldName")` (reflection-cached)
- ✅ Auto-derives table/column names from `@Table` / `@Column` or snake_case conversion
- ✅ Full SQL support: `SELECT`, `DISTINCT`, `DISTINCT ON`, all `JOIN` types, `WHERE`, `GROUP BY`, `ORDER BY`, `LIMIT`, `OFFSET`
- ✅ Predicate composition: `and()`, `or()`, `equals()`, `in()`, `notIn()`, `like()`, `between()`, `isNull()`, and more
- ✅ String functions: `lower()`, `upper()`, `concat()`, `toChar()`
- ✅ Aggregate functions: `count()` on root and column
- ✅ Pagination support with Spring's `Pageable`

---

## Requirements

| Dependency             | Version |
| ---------------------- | ------- |
| Java                   | 17+     |
| Spring Data Relational | 3.x     |
| Spring Boot (R2DBC)    | 3.x     |
| Lombok                 | 1.18+   |

---

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.YOUR_GITHUB_USERNAME</groupId>
    <artifactId>custom-query-builder</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.YOUR_GITHUB_USERNAME:custom-query-builder:1.0.0'
```

---

## Quick Start

```java
@Component
public class ProductRepository {

    private final DatabaseClient databaseClient;

    public ProductRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Flux<ProductResponse> findActiveProducts(String search, List<UUID> categoryIds) {
        CriteriaBuilder cb = new CriteriaBuilder();
        Root<Product> root = cb.from(Product.class);
        Root<Category> categoryRoot = cb.join(
            Category.class, root.get("categoryId"), "id", JoinType.LEFT_OUTER_JOIN
        );

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equals(root.get("deleted"), false));

        if (search != null)
            predicates.add(cb.like(cb.lower(root.get("name")), search.toLowerCase(), LikeOperator.ALL));

        if (categoryIds != null && !categoryIds.isEmpty())
            predicates.add(cb.in(root.get("categoryId"), categoryIds));

        return Flux.just(cb.customQueryBuilder())
            .map(q -> q.select(cb.multiSelect(
                    root.get("id"),
                    root.get("name"),
                    root.get("price"),
                    categoryRoot.get("name", "category_name")
                ))
                .where(cb.and(predicates.toArray(Predicate[]::new)))
                .orderBy(cb.asc(root.get("name")))
            )
            .map(CustomQuery::getQuery)
            .map(databaseClient::sql)
            .flatMap(spec -> spec.mapProperties(ProductResponse.class).all());
    }
}
```

---

## API Reference

### CriteriaBuilder

| Method                                                                                                                                       | Description                       |
| -------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------- |
| `from(Class<T>)`                                                                                                                             | Declares the primary FROM table   |
| `join(Class<T>, Column, String, JoinType)`                                                                                                   | Adds a JOIN                       |
| `customQueryBuilder()`                                                                                                                       | Returns a new `CustomQuery`       |
| `select(Root)`                                                                                                                               | `SELECT DISTINCT alias.*`         |
| `multiSelect(Column...)`                                                                                                                     | `SELECT DISTINCT col1, col2, ...` |
| `count(Root)` / `count(Column...)`                                                                                                           | `SELECT COUNT(DISTINCT ...)`      |
| `selectDistinctOn(Column...)`                                                                                                                | PostgreSQL `DISTINCT ON (...)`    |
| `equals`, `in`, `notIn`, `like`, `notLike`, `between`, `greaterThan`, `lessThan`, `greaterThanEqual`, `lessThanEqual`, `isNull`, `isNotNull` | Predicate builders                |
| `and(Predicate...)` / `or(Predicate...)`                                                                                                     | Logical combinators               |
| `lower`, `upper`, `concat`                                                                                                                   | String function wrappers          |
| `asc(Column)` / `desc(Column)`                                                                                                               | Sort order builders               |

### CustomQuery (fluent)

| Method                  | Description                          |
| ----------------------- | ------------------------------------ |
| `.select(Select)`       | Sets the SELECT clause               |
| `.where(Predicate)`     | Sets the WHERE clause                |
| `.distinctOn(Distinct)` | Sets DISTINCT ON (PostgreSQL)        |
| `.distinct(boolean)`    | Keeps or strips DISTINCT             |
| `.orderBy(Order...)`    | Sets ORDER BY                        |
| `.groupBy(Column...)`   | Sets GROUP BY                        |
| `.limit(int)`           | Sets LIMIT                           |
| `.offset(long)`         | Sets OFFSET (requires limit > 0)     |
| `.getQuery()`           | Assembles and returns the SQL string |

### JoinType

`JOIN` · `LEFT_OUTER_JOIN` · `RIGHT_OUTER_JOIN` · `FULL_OUTER_JOIN` · `CROSS_JOIN`

### LikeOperator

`ALL` (`%value%`) · `START` (`value%`) · `END` (`%value`)

---

## Contributing

Pull requests are welcome. For major changes, please open an issue first.

---

## License

[MIT](LICENSE)
