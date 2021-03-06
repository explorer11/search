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
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.search.Query;

import java.io.IOException;

public class LatLonPointDistanceQuery extends AbstractFieldQuery<LatLonPointDistanceQuery> {

	final public double center_latitude;

	final public double center_longitude;

	final public double radius_meters;

	@JsonCreator
	public LatLonPointDistanceQuery(@JsonProperty("generic_field") final String genericField,
			@JsonProperty("field") final String field, @JsonProperty("center_latitude") final double centerLatitude,
			@JsonProperty("center_longitude") final double centerLongitude,
			@JsonProperty("radius_meters") final double radiusMeters) {
		super(LatLonPointDistanceQuery.class, genericField, field);
		this.center_latitude = centerLatitude;
		this.center_longitude = centerLongitude;
		this.radius_meters = radiusMeters;
	}

	@Override
	@JsonIgnore
	protected boolean isEqual(LatLonPointDistanceQuery q) {
		return super.isEqual(q) && center_latitude == q.center_latitude && center_longitude == q.center_longitude &&
				radius_meters == q.radius_meters;
	}

	@Override
	final public Query getQuery(final QueryContext queryContext) {
		return LatLonPoint.newDistanceQuery(resolveField(queryContext.getFieldMap()), center_latitude, center_longitude,
				radius_meters);
	}
}
