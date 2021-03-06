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
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.Arrays;

public class LongSetQuery extends AbstractFieldQuery<LongSetQuery> {

	final public long[] values;

	@JsonCreator
	public LongSetQuery(@JsonProperty("generic_field") final String genericField,
			@JsonProperty("field") final String field, @JsonProperty("values") final long... values) {
		super(LongSetQuery.class, genericField, field);
		this.values = values;
	}

	public LongSetQuery(final String field, final long... values) {
		this(null, field, values);
	}

	@Override
	@JsonIgnore
	protected boolean isEqual(LongSetQuery q) {
		return super.isEqual(q) && Arrays.equals(values, q.values);
	}

	@Override
	public Query getQuery(final QueryContext queryContext) throws IOException {
		return LongPoint.newSetQuery(resolveField(queryContext.getFieldMap()), values);
	}
}
