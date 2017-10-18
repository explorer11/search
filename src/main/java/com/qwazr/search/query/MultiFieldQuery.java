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
import com.qwazr.search.analysis.TermConsumer;
import com.qwazr.search.index.QueryContext;
import com.qwazr.utils.CollectionsUtils;
import com.qwazr.utils.FunctionUtils;
import com.qwazr.utils.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MultiFieldQuery extends AbstractQuery<MultiFieldQuery> {

	@JsonProperty("fields_boosts")
	final public Map<String, Float> fieldsBoosts;

	@JsonProperty("fields_disabled_graph")
	final public Set<String> fieldsDisabledGraph;

	@JsonProperty("fields_and_filter")
	final public Set<String> fieldsAndFilter;

	@JsonProperty("default_operator")
	final public QueryParserOperator defaultOperator;

	@JsonProperty("query_string")
	final public String queryString;

	@JsonProperty("min_number_should_match")
	final public Integer minNumberShouldMatch;

	@JsonProperty("tie_breaker_multiplier")
	final public Float tieBreakerMultiplier;

	final private Analyzer analyzer;

	public MultiFieldQuery(final QueryParserOperator defaultOperator, final String queryString) {
		this(new LinkedHashMap<>(), new LinkedHashSet<>(), defaultOperator, queryString, null, null, null);
	}

	public MultiFieldQuery(final QueryParserOperator defaultOperator, final String queryString,
			final Integer minNumberShouldMatch) {
		this(new LinkedHashMap<>(), new LinkedHashSet<>(), defaultOperator, queryString, minNumberShouldMatch, null,
				null);
	}

	public MultiFieldQuery(final QueryParserOperator defaultOperator, final String queryString,
			final Integer minNumberShouldMatch, final Float tieBreakerMultiplier) {
		this(new LinkedHashMap<>(), new LinkedHashSet<>(), defaultOperator, queryString, minNumberShouldMatch,
				tieBreakerMultiplier, null);
	}

	public MultiFieldQuery(final Map<String, Float> fieldsBoosts, final Set<String> fieldsGraphs,
			final QueryParserOperator defaultOperator, final String queryString, final Integer minNumberShouldMatch) {
		this(fieldsBoosts, fieldsGraphs, defaultOperator, queryString, minNumberShouldMatch, null, null);
	}

	public MultiFieldQuery(final QueryParserOperator defaultOperator, final String queryString,
			final Integer minNumberShouldMatch, final Float tieBreakerMultiplier, final Analyzer analyzer) {
		this(new LinkedHashMap<>(), new LinkedHashSet<>(), defaultOperator, queryString, minNumberShouldMatch,
				tieBreakerMultiplier, analyzer);
	}

	public MultiFieldQuery(final Map<String, Float> fieldsBoosts, final Set<String> fieldsDisabledGraph,
			final QueryParserOperator defaultOperator, final String queryString, final Integer minNumberShouldMatch,
			final Float tieBreakerMultiplier, final Analyzer analyzer) {
		this(fieldsBoosts, fieldsDisabledGraph, new LinkedHashSet<>(), defaultOperator, queryString,
				minNumberShouldMatch, tieBreakerMultiplier, analyzer);
	}

	public MultiFieldQuery(final Map<String, Float> fieldsBoosts, final Set<String> fieldsDisabledGraph,
			final Set<String> fieldsAndFilter, final QueryParserOperator defaultOperator, final String queryString,
			final Integer minNumberShouldMatch, final Float tieBreakerMultiplier, final Analyzer analyzer) {
		super(MultiFieldQuery.class);
		this.fieldsBoosts = fieldsBoosts;
		this.fieldsDisabledGraph = fieldsDisabledGraph;
		this.fieldsAndFilter = fieldsAndFilter;
		this.defaultOperator = defaultOperator;
		this.queryString = queryString;
		this.minNumberShouldMatch = minNumberShouldMatch;
		this.tieBreakerMultiplier = tieBreakerMultiplier;
		this.analyzer = analyzer;
	}

	@JsonCreator
	public MultiFieldQuery(@JsonProperty("fields_boosts") final Map<String, Float> fieldsBoosts,
			@JsonProperty("fields_disabled_graph") final Set<String> fieldsDisabledGraph,
			@JsonProperty("fields_and_filter") final Set<String> fieldsAndFilter,
			@JsonProperty("default_operator") final QueryParserOperator defaultOperator,
			@JsonProperty("query_string") final String queryString,
			@JsonProperty("min_number_should_match") final Integer minNumberShouldMatch,
			@JsonProperty("tie_breaker_multiplier") final Float tieBreakerMultiplier) {
		this(fieldsBoosts, fieldsDisabledGraph, fieldsAndFilter, defaultOperator, queryString, minNumberShouldMatch,
				tieBreakerMultiplier, null);
	}

	@JsonIgnore
	public MultiFieldQuery field(final String field, final Float boost, final boolean enableGraph,
			final boolean andFilter) {
		Objects.requireNonNull(field, "The field is missing");
		Objects.requireNonNull(fieldsBoosts);
		if (boost != null)
			fieldsBoosts.put(field, boost);
		else
			fieldsBoosts.put(field, 1.0F);
		if (enableGraph)
			fieldsDisabledGraph.remove(field);
		else
			fieldsDisabledGraph.add(field);
		if (andFilter)
			fieldsAndFilter.add(field);
		else
			fieldsAndFilter.remove(field);
		return this;
	}

	@JsonIgnore
	public MultiFieldQuery field(final String field, final Float boost, final boolean enableGraph) {
		return field(field, boost, enableGraph, false);
	}

	@JsonIgnore
	public MultiFieldQuery field(final String field, final Float boost) {
		return field(field, boost, true, false);
	}

	@Override
	protected boolean isEqual(MultiFieldQuery q) {
		return CollectionsUtils.equals(fieldsBoosts, q.fieldsBoosts) &&
				CollectionsUtils.equals(fieldsDisabledGraph, q.fieldsDisabledGraph) &&
				CollectionsUtils.equals(fieldsAndFilter, q.fieldsAndFilter) &&
				Objects.equals(defaultOperator, q.defaultOperator) && Objects.equals(queryString, q.queryString) &&
				Objects.equals(minNumberShouldMatch, q.minNumberShouldMatch) &&
				Objects.equals(tieBreakerMultiplier, q.tieBreakerMultiplier) && Objects.equals(analyzer, q.analyzer);
	}

	@Override
	final public Query getQuery(final QueryContext queryContext) throws IOException, ReflectiveOperationException {
		Objects.requireNonNull(fieldsBoosts, "Fields boosts is missing");

		if (StringUtils.isEmpty(queryString))
			return new org.apache.lucene.search.MatchNoDocsQuery();

		// Select the right analyzer
		final Analyzer alzr = analyzer != null ? analyzer : queryContext.getQueryAnalyzer();

		// We look for terms frequency globally
		final Map<String, Integer> termsFreq = new HashMap<>();
		final IndexReader indexReader = queryContext.getIndexReader();
		FunctionUtils.forEachEx(fieldsBoosts, (field, boost) -> {
			try (final TokenStream tokenStream = alzr.tokenStream(field, queryString)) {
				new TermsWithFreq(tokenStream, indexReader, field, termsFreq).forEachToken();
				tokenStream.end();
			}
		});

		// Build the per field queries
		final List<Query> fieldQueries = new ArrayList<>();
		final List<Query> andFieldQueries = fieldsAndFilter != null ? new ArrayList<>() : null;
		final BooleanClause.Occur defaultOccur = defaultOperator == null || defaultOperator == QueryParserOperator.AND ?
				BooleanClause.Occur.MUST :
				BooleanClause.Occur.SHOULD;
		fieldsBoosts.forEach((field, boost) -> {
			final List<Query> queries;
			final BooleanClause.Occur occur;
			if (fieldsAndFilter != null && fieldsAndFilter.contains(field)) {
				queries = andFieldQueries;
				occur = minNumberShouldMatch != null ? BooleanClause.Occur.SHOULD : BooleanClause.Occur.MUST;
			} else {
				queries = fieldQueries;
				occur = minNumberShouldMatch != null ? BooleanClause.Occur.SHOULD : defaultOccur;
			}
			final Query query = new FieldQueryBuilder(alzr, field, termsFreq).parse(queryString, occur, boost);
			if (query != null)
				queries.add(query);
		});

		// Build the final query
		final Query fieldsQuery = getRootQuery(fieldQueries);
		if (andFieldQueries == null || andFieldQueries.isEmpty())
			return fieldsQuery;
		final Query andFieldsQuery = getRootQuery(andFieldQueries);
		final BooleanQuery.Builder builder = new org.apache.lucene.search.BooleanQuery.Builder();
		builder.add(fieldsQuery, BooleanClause.Occur.SHOULD);
		builder.add(andFieldsQuery, BooleanClause.Occur.MUST);
		return builder.build();
	}

	protected Query getRootQuery(final Collection<Query> queries) {
		if (queries.size() == 1)
			return queries.iterator().next();
		if (tieBreakerMultiplier != null) {
			return new org.apache.lucene.search.DisjunctionMaxQuery(queries, tieBreakerMultiplier);
		} else {
			final BooleanQuery.Builder builder = new org.apache.lucene.search.BooleanQuery.Builder();
			queries.forEach(query -> {
				if (query != null)
					builder.add(query, BooleanClause.Occur.SHOULD);
			});
			return builder.build();
		}
	}

	protected Query getTermQuery(final int freq, final Term term) {
		Query query;
		if (freq > 0)
			query = new org.apache.lucene.search.TermQuery(term);
		else
			query = new org.apache.lucene.search.FuzzyQuery(term);
		return query;
	}

	private class TermsWithFreq extends TermConsumer.WithChar {

		private final IndexReader indexReader;
		private final String field;
		private final Map<String, Integer> termsFreq;

		private TermsWithFreq(final TokenStream tokenStream, final IndexReader indexReader, final String field,
				final Map<String, Integer> termsFreq) {
			super(tokenStream);
			this.indexReader = indexReader;
			this.field = field;
			this.termsFreq = termsFreq;
		}

		@Override
		final public boolean token() throws IOException {
			final String text = charTermAttr.toString();
			final Term term = new Term(field, text);
			final int newFreq = indexReader == null ? 0 : indexReader.docFreq(term);
			if (newFreq > 0) {
				final Integer previousFreq = termsFreq.get(text);
				if (previousFreq == null || newFreq > previousFreq)
					termsFreq.put(text, newFreq);
			}
			return true;
		}
	}

	final class FieldQueryBuilder extends org.apache.lucene.util.QueryBuilder {

		final Map<String, Integer> termsFreq;
		final String field;

		private FieldQueryBuilder(final Analyzer analyzer, final String field, final Map<String, Integer> termsFreq) {
			super(analyzer);
			this.termsFreq = termsFreq;
			this.field = field;
			setEnableGraphQueries(fieldsDisabledGraph == null || !fieldsDisabledGraph.contains(field));
		}

		@Override
		final protected Query newTermQuery(Term term) {
			final Integer freq = termsFreq.get(term.text());
			return getTermQuery(freq == null ? 0 : freq, term);
		}

		protected BooleanQuery.Builder newBooleanQuery() {
			return new FieldBooleanBuilder();
		}

		final Query parse(final String queryString, final BooleanClause.Occur defaultOperator, final Float boost) {
			final Query fieldQuery = createBooleanQuery(field, queryString, defaultOperator);
			return boost != null && boost != 1.0F && fieldQuery != null ?
					new org.apache.lucene.search.BoostQuery(fieldQuery, boost) :
					fieldQuery;
		}

	}

	final class FieldBooleanBuilder extends BooleanQuery.Builder {

		private int clauseCount;

		@Override
		public BooleanQuery.Builder add(BooleanClause clause) {
			clauseCount++;
			return super.add(clause);
		}

		@Override
		public BooleanQuery build() {
			if (minNumberShouldMatch != null) {
				final int minShouldMatch = Math.round(Math.max(1, (float) (clauseCount * minNumberShouldMatch) / 100));
				setMinimumNumberShouldMatch(minShouldMatch);
			}
			return super.build();
		}
	}

}
