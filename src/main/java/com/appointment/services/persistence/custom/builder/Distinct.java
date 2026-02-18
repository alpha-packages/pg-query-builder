package com.appointment.services.persistence.custom.builder;

import lombok.Builder;
import lombok.Getter;

/**
 * Carries the {@code DISTINCT ON (...)} clause fragment used in PostgreSQL queries.
 *
 * <p>Created via {@link CriteriaBuilder#selectDistinctOn(Column...)} and applied
 * to a {@link CustomQuery} via {@link CustomQuery#distinctOn(Distinct)}.
 *
 * <p><b>Example output stored in {@code data}:</b>
 * <pre>
 *   "DISTINCT ON (m.id)"
 *   "DISTINCT ON (m.provider_id, m.availability_date)"
 * </pre>
 *
 * <p><b>Note:</b> {@code DISTINCT ON} and {@code DISTINCT} are mutually exclusive
 * within the same query. Using both will throw {@link IllegalArgumentException}.
 */
@Getter
@Builder
public final class Distinct {

    /**
     * The {@code DISTINCT ON (...)} expression string injected into the SELECT clause.
     */
    private final String data;
}
