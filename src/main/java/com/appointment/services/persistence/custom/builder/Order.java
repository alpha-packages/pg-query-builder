package com.appointment.services.persistence.custom.builder;

import com.appointment.services.persistence.custom.constants.OrderType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single ORDER BY term, pairing a {@link Column} with a sort direction.
 *
 * <p>Created via {@link CriteriaBuilder#asc(Column)} or {@link CriteriaBuilder#desc(Column)}
 * and passed to {@link CustomQuery#orderBy(Order...)}.
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * cb.customQueryBuilder()
 *     .select(cb.select(root))
 *     .orderBy(cb.asc(root.get("name")), cb.desc(root.get("createdAt")))
 *     .getQuery();
 * }</pre>
 */
@Getter
@Setter
@Builder
public final class Order {

    /** The column to sort by. */
    private Column columnName;

    /** Sort direction: {@link OrderType#ASC} or {@link OrderType#DESC}. */
    private OrderType orderType;
}
