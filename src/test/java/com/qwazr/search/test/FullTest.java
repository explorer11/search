/**
 * Copyright 2015 Emmanuel Keller / QWAZR
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qwazr.search.test;

import com.google.common.io.Files;
import com.qwazr.search.SearchServer;
import com.qwazr.search.index.*;
import com.qwazr.utils.IOUtils;
import com.qwazr.utils.json.JsonMapper;
import org.apache.commons.cli.ParseException;
import org.junit.*;
import org.junit.runners.MethodSorters;

import javax.servlet.ServletException;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class FullTest {

	private static volatile boolean started;

	public static final String BASE_URL = "http://localhost:9091";
	public static final String SCHEMA_NAME = "schema-test";
	public static final String INDEX_NAME = "index-test";
	public static final Map<String, FieldDefinition> FIELDS_JSON = getFieldMap("fields.json");
	public static final QueryDefinition MATCH_ALL_QUERY = getQuery("query_match_all.json");
	public static final QueryDefinition FACETS_ROWS_QUERY = getQuery("query_facets_rows.json");
	public static final QueryDefinition DELETE_QUERY = getQuery("query_delete.json");
	public static final Map<String, Object> UPDATE_DOC = getDoc("update_doc.json");
	public static final List<Map<String, Object>> UPDATE_DOCS = getDocs("update_docs.json");

	@Before
	public void startServer() throws IOException, ParseException, ServletException, IllegalAccessException,
					InstantiationException {
		if (started)
			return;
		// start the server
		File dataDir = Files.createTempDir();
		SearchServer.main(new String[] { "-d", dataDir.getAbsolutePath() });
		started = true;
	}

	private IndexServiceInterface getClient() throws URISyntaxException {
		return new IndexSingleClient(BASE_URL, 60000);
	}

	@Test
	public void test000CreateSchema() throws URISyntaxException {
		IndexServiceInterface client = getClient();
		Response response = client.createUpdateSchema(SCHEMA_NAME, null);
		Assert.assertNotNull(response);
		Assert.assertEquals(201, response.getStatusInfo().getStatusCode());
	}

	@Test
	public void test001UpdateSchema() throws URISyntaxException {
		IndexServiceInterface client = getClient();
		Response response = client.createUpdateSchema(SCHEMA_NAME, null);
		Assert.assertNotNull(response);
		Assert.assertEquals(202, response.getStatusInfo().getStatusCode());
	}

	@Test
	public void test002ListSchema() throws URISyntaxException {
		IndexServiceInterface client = getClient();
		Set<String> schemas = client.getSchemas(null);
		Assert.assertNotNull(schemas);
		Assert.assertEquals(schemas.size(), 1);
		Assert.assertTrue(schemas.contains(SCHEMA_NAME));
	}

	private static Map<String, FieldDefinition> getFieldMap(String res) {
		InputStream is = FullTest.class.getResourceAsStream(res);
		try {
			return FieldDefinition.newFieldMap(IOUtils.toString(is));
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.close(is);
		}
	}

	@Test
	public void test100CreateIndex() throws URISyntaxException, IOException {
		IndexServiceInterface client = getClient();
		Map<String, FieldDefinition> fieldMap = FIELDS_JSON;
		IndexStatus indexStatus = client.createUpdateIndex(SCHEMA_NAME, INDEX_NAME, null, fieldMap);
		Assert.assertNotNull(indexStatus);
		Assert.assertNotNull(indexStatus.fields);
		Assert.assertEquals(fieldMap.size(), indexStatus.fields.size());
	}

	private static QueryDefinition getQuery(String res) {
		InputStream is = FullTest.class.getResourceAsStream(res);
		try {
			return QueryDefinition.newQuery(IOUtils.toString(is));
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.close(is);
		}
	}

	private ResultDefinition checkQuerySchema(IndexServiceInterface client, QueryDefinition queryDef, int expectedCount)
					throws IOException {
		ResultDefinition result = client.searchQuery(SCHEMA_NAME, queryDef, null);
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.total_hits);
		Assert.assertEquals(expectedCount, result.total_hits.intValue());
		return result;
	}

	private ResultDefinition checkQueryIndex(IndexServiceInterface client, QueryDefinition queryDef, int expectedCount)
					throws IOException {
		ResultDefinition result = client.searchQuery(SCHEMA_NAME, INDEX_NAME, queryDef, null);
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.total_hits);
		Assert.assertEquals(expectedCount, result.total_hits.intValue());
		return result;
	}

	private void checkIndexSize(IndexServiceInterface client, long expectedCount) throws IOException {
		IndexStatus status = client.getIndex(SCHEMA_NAME, INDEX_NAME);
		Assert.assertNotNull(status);
		Assert.assertNotNull(status.num_docs);
		Assert.assertEquals(expectedCount, status.num_docs.longValue());
	}

	@Test
	public void test110SearchEmptySchema() throws URISyntaxException, IOException {
		IndexServiceInterface client = getClient();
		checkAllSizes(client, 0);
	}

	private void checkAllSizes(IndexServiceInterface client, int expectedSize) throws IOException {
		Runnable task = () -> {
			String threadName = Thread.currentThread().getName();
			System.out.println("Hello " + threadName);
		};

		checkQuerySchema(client, MATCH_ALL_QUERY, expectedSize);
		checkQueryIndex(client, MATCH_ALL_QUERY, expectedSize);
		checkIndexSize(client, expectedSize);
	}

	private static List<Map<String, Object>> getDocs(String res) {
		InputStream is = FullTest.class.getResourceAsStream(res);
		try {
			return JsonMapper.MAPPER.readValue(is, IndexSingleClient.ListMapStringObjectTypeRef);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.close(is);
		}
	}

	private static Map<String, Object> getDoc(String res) {
		InputStream is = FullTest.class.getResourceAsStream(res);
		try {
			return JsonMapper.MAPPER.readValue(is, IndexSingleClient.MapStringObjectTypeRef);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.close(is);
		}
	}

	@Test
	public void test200UpdateDocs() throws URISyntaxException, IOException {
		IndexServiceInterface client = getClient();
		Response response = client.postDocuments(SCHEMA_NAME, INDEX_NAME, UPDATE_DOCS);
		Assert.assertNotNull(response);
		Assert.assertEquals(200, response.getStatusInfo().getStatusCode());
		checkAllSizes(client, 4);
	}

	private BackupStatus doBackup(IndexServiceInterface client) {
		BackupStatus status = client.doBackup(SCHEMA_NAME, INDEX_NAME);
		Assert.assertNotNull(status);
		Assert.assertNotNull(status.date);
		Assert.assertNotNull(status.bytes_size);
		Assert.assertTrue(status.bytes_size > 0);
		Assert.assertNotNull(status.files_count);
		Assert.assertTrue(status.files_count > 0);
		return status;
	}

	private List<BackupStatus> getBackups(IndexServiceInterface client, int expectedSize) {
		List<BackupStatus> backups = client.getBackups(SCHEMA_NAME, INDEX_NAME);
		Assert.assertNotNull(backups);
		Assert.assertEquals(expectedSize, backups.size());
		return backups;
	}

	@Test
	public void test250FirstBackup() throws URISyntaxException, IOException {
		IndexServiceInterface client = getClient();
		List<BackupStatus> backups = client.getBackups(SCHEMA_NAME, INDEX_NAME);
		Assert.assertNotNull(backups);
		Assert.assertTrue(backups.isEmpty());
		BackupStatus status = doBackup(client);
		Assert.assertEquals(status, getBackups(client, 1).get(0));
	}

	@Test
	public void test300UpdateDoc() throws URISyntaxException, IOException {
		IndexServiceInterface client = getClient();
		Response response = client.postDocument(SCHEMA_NAME, INDEX_NAME, UPDATE_DOC);
		Assert.assertNotNull(response);
		Assert.assertEquals(200, response.getStatusInfo().getStatusCode());
		checkAllSizes(client, 5);
	}

	private void checkFacetRowsQuery(ResultDefinition result) {
		Assert.assertNotNull(result.documents);
		Assert.assertEquals(3, result.documents.size());
		for (ResultDefinition.ResultDocument doc : result.documents) {
			Assert.assertNotNull(doc.fields);
			Assert.assertEquals(2, doc.fields.size());
			Assert.assertTrue(doc.fields.containsKey("name"));
			Assert.assertTrue(doc.fields.get("name") instanceof String);
			Assert.assertTrue(doc.fields.containsKey("price"));
			Assert.assertTrue(doc.fields.get("price") instanceof Double);
		}
		Assert.assertNotNull(result.facets);
		Assert.assertTrue(result.facets.containsKey("category"));
	}

	@Test
	public void test400QueryDoc() throws URISyntaxException, IOException {
		IndexServiceInterface client = getClient();
		checkFacetRowsQuery(checkQueryIndex(client, FACETS_ROWS_QUERY, 5));
		checkFacetRowsQuery(checkQuerySchema(client, FACETS_ROWS_QUERY, 5));
	}

	@Test
	public void test500SecondBackup() throws URISyntaxException, IOException {
		IndexServiceInterface client = getClient();
		BackupStatus status = doBackup(client);
		Assert.assertEquals(status, getBackups(client, 2).get(0));
		// Same backup again
		status = doBackup(client);
		Assert.assertEquals(status, getBackups(client, 2).get(0));
	}

	@Test
	public void test600UpdateSchemaIndex() throws URISyntaxException, IOException {
		IndexServiceInterface client = getClient();
		IndexStatus indexStatus = client.createUpdateIndex(SCHEMA_NAME, INDEX_NAME, null, FIELDS_JSON);
		Assert.assertNotNull(indexStatus);
		Assert.assertNotNull(indexStatus.fields);
		Assert.assertEquals(FIELDS_JSON.size(), indexStatus.fields.size());
	}

	@Test
	public void test700DeleteDoc() throws URISyntaxException, IOException {
		IndexServiceInterface client = getClient();
		ResultDefinition result = client.searchQuery(SCHEMA_NAME, INDEX_NAME, DELETE_QUERY, true);
		Assert.assertNotNull(result);
		Assert.assertNotNull(result.total_hits);
		Assert.assertEquals(2L, (long) result.total_hits);
		checkAllSizes(client, 3);
	}

	@Test
	public void test800ThirdBackup() throws URISyntaxException, IOException {
		IndexServiceInterface client = getClient();
		BackupStatus status = doBackup(client);
		Assert.assertEquals(status, getBackups(client, 3).get(0));
		client.purgeBackups(SCHEMA_NAME, INDEX_NAME, 2);
		Assert.assertEquals(status, getBackups(client, 2).get(0));
	}

	@Test
	public void test980DeleteIndex() throws URISyntaxException {
		IndexServiceInterface client = getClient();
		Response response = client.deleteIndex(SCHEMA_NAME, INDEX_NAME, null);
		Assert.assertNotNull(response);
		Assert.assertEquals(200, response.getStatusInfo().getStatusCode());
	}

	@Test
	public void test981EmptyIndex() throws URISyntaxException {
		IndexServiceInterface client = getClient();
		Set<String> indexes = client.getIndexes(SCHEMA_NAME, null);
		Assert.assertNotNull(indexes);
		Assert.assertTrue(indexes.isEmpty());
	}

	@Test
	public void test990DeleteSchema() throws URISyntaxException {
		IndexServiceInterface client = getClient();
		Response response = client.deleteSchema(SCHEMA_NAME, null);
		Assert.assertNotNull(response);
		Assert.assertEquals(200, response.getStatusInfo().getStatusCode());

	}

	@Test
	public void test991EmptySchema() throws URISyntaxException {
		IndexServiceInterface client = getClient();
		Set<String> schemas = client.getSchemas(null);
		Assert.assertNotNull(schemas);
		Assert.assertTrue(schemas.isEmpty());
	}

}
