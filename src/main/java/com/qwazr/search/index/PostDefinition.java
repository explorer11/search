/**
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
package com.qwazr.search.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class PostDefinition {

	@JsonProperty("commit_user_data")
	final public Map<String, String> commitUserData;

	public PostDefinition() {
		commitUserData = null;
	}

	PostDefinition(Map<String, String> commitUserData) {
		this.commitUserData = commitUserData;
	}

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	public static class Document extends PostDefinition {

		final public Map<String, Object> document;

		public Document() {
			document = null;
		}

		Document(final Map<String, Object> document, final Map<String, String> commitUserData) {
			super(commitUserData);
			this.document = document;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	public static class Documents extends PostDefinition {

		final public List<Map<String, Object>> documents;

		public Documents() {
			documents = null;
		}

		Documents(final List<Map<String, Object>> documents, final Map<String, String> commitUserData) {
			super(commitUserData);
			this.documents = documents;
		}
	}

	public static Document of(final Map<String, Object> document, final Map<String, String> commitUserData) {

		return new PostDefinition.Document(document, commitUserData);
	}

	public static Documents of(final List<Map<String, Object>> documents, final Map<String, String> commitUserData) {

		return new PostDefinition.Documents(documents, commitUserData);
	}
}