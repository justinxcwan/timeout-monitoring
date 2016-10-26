/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dataartisans.timeoutmonitoring.session;

import com.dataartisans.timeoutmonitoring.Function;
import com.dataartisans.timeoutmonitoring.JSONObjectExtractor;
import org.json.JSONObject;

public class LatencyTimeoutFunction implements Function<JSONObject, JSONObject> {
	private final String[] outputFields;
	private final long timeout;

	public LatencyTimeoutFunction(String[] outputFields, long timeout) {
		this.outputFields = outputFields;
		this.timeout = timeout;
	}

	@Override
	public JSONObject apply(JSONObject jsonObject) {
		JSONObject result = JSONObjectExtractor.createJSONObject(jsonObject, outputFields);

		result.put("sessionTimeout", "true");
		result.put("latency", timeout + "");

		return result;
	}
}
