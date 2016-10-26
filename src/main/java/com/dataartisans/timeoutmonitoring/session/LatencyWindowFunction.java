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

import com.dataartisans.timeoutmonitoring.Function2;
import com.dataartisans.timeoutmonitoring.JSONObjectExtractor;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONObject;
import scala.Serializable;

public class LatencyWindowFunction implements Function2<JSONObject, JSONObject, JSONObject>, Serializable {

	private final String[] resultFields;

	public LatencyWindowFunction(String[] resultFields) {
		this.resultFields = resultFields;
	}

	@Override
	public JSONObject apply(JSONObject left, JSONObject right) {

		JSONObject result = JSONObjectExtractor.createJSONObject(left, resultFields);

		String firstTimestamp = left.getString("timestamp");
		String lastTimestamp = right.getString("timestamp");

		DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
		DateTime firstDateTime = formatter.parseDateTime(firstTimestamp);
		DateTime lastDateTime = formatter.parseDateTime(lastTimestamp);

		Duration diff = new Duration(firstDateTime, lastDateTime);

		result.put("latency", diff.getMillis() + "");

		return result;
	}
}
