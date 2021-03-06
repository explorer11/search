/*
 * Copyright 2015-2017 Emmanuel Keller / QWAZR
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.qwazr.search.index.QueryContext;
import com.qwazr.utils.ArrayUtils;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

public class LongMultiRangeQuery extends AbstractMultiRangeQuery<LongMultiRangeQuery> {

	final public long[] lower_values;
	final public long[] upper_values;

	@JsonCreator
	public LongMultiRangeQuery(@JsonProperty("generic_field") final String genericField,
			@JsonProperty("field") final String field, @JsonProperty("lower_values") final long[] lowerValues,
			@JsonProperty("upper_values") final long[] upperValues) {
		super(LongMultiRangeQuery.class, genericField, field);
		this.lower_values = lowerValues;
		this.upper_values = upperValues;
	}

	public LongMultiRangeQuery(final String field, final long lowerValue, final long upperValue) {
		this(null, field, new long[] { lowerValue }, new long[] { upperValue });
	}

	@Override
	@JsonIgnore
	protected boolean isEqual(LongMultiRangeQuery q) {
		return super.isEqual(q) && Arrays.equals(lower_values, q.lower_values) &&
				Arrays.equals(upper_values, q.upper_values);
	}

	@Override
	public Query getQuery(final QueryContext queryContext) throws IOException {
		final String resolvedField = resolveField(queryContext.getFieldMap());
		if (lower_values.length == 1)
			return LongPoint.newRangeQuery(resolvedField, lower_values[0], upper_values[0]);
		else
			return LongPoint.newRangeQuery(resolvedField, lower_values, upper_values);
	}

	public static class Builder extends AbstractBuilder<Long, Builder> {

		public Builder(String genericField, String field) {
			super(genericField, field);
		}

        @Override
        protected Builder me() {
            return this;
        }

        @Override
		protected LongMultiRangeQuery build(final String field, final Collection<Long> lowerValues,
				final Collection<Long> upperValues) {
			return new LongMultiRangeQuery(genericField, field, ArrayUtils.toPrimitiveLong(lowerValues),
					ArrayUtils.toPrimitiveLong(upperValues));
		}

	}

}
