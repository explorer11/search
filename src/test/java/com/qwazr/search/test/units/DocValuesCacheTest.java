/*
 * Copyright 2017 Emmanuel Keller / QWAZR
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
package com.qwazr.search.test.units;

import com.qwazr.search.field.FieldDefinition;
import com.qwazr.search.index.QueryDefinition;
import com.qwazr.search.index.ResultDefinition;
import com.qwazr.search.query.TermQuery;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;

public class DocValuesCacheTest extends AbstractIndexTest {

	@Before
	public void setup() throws IOException, URISyntaxException {
		initIndexService();
	}

	void checkDocValue(Double expectedValue) {
		ResultDefinition.WithObject<IndexRecord> result = indexService.searchQuery(QueryDefinition.of(
				new TermQuery(FieldDefinition.ID_FIELD, "1")).returnedField("*").build());
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.total_hits);
		Assert.assertEquals(1L, result.total_hits, 0);
		IndexRecord record = result.documents.get(0).record;
		Assert.assertEquals(expectedValue, record.doubleDocValue);
	}

	@Test
	public void checkUpdateDv() throws IOException, InterruptedException {
		indexService.postDocument(new IndexRecord("1"));
		checkDocValue(null);
		indexService.postDocument(new IndexRecord("1").doubleDocValue(1.11d));
		checkDocValue(1.11d);
		checkDocValue(1.11d);
		indexService.postDocument(new IndexRecord("1").doubleDocValue(2.22d));
		checkDocValue(2.22d);
	}
}