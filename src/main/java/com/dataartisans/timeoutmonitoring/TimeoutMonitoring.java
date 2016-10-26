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

package com.dataartisans.timeoutmonitoring;

import com.dataartisans.timeoutmonitoring.alert.Alert;
import com.dataartisans.timeoutmonitoring.alert.AlertWindowOperator;
import com.dataartisans.timeoutmonitoring.alert.JSONObjectAlertFunction;
import com.dataartisans.timeoutmonitoring.predicate.JSONObjectPredicateAnd;
import com.dataartisans.timeoutmonitoring.predicate.JSONObjectPredicateMatchEquals;
import com.dataartisans.timeoutmonitoring.predicate.JSONObjectPredicateMatchRegex;
import com.dataartisans.timeoutmonitoring.session.JSONSessionMonitoring;
import com.dataartisans.timeoutmonitoring.session.LatencyTimeoutFunction;
import com.dataartisans.timeoutmonitoring.session.LatencyWindowFunction;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.json.JSONObject;

import java.util.regex.Pattern;

public class TimeoutMonitoring {

	public static void main(String[] args) throws Exception {
		ParameterTool params = ParameterTool.fromArgs(args);

		String filePath = params.get("filePath");
		String delayStr = params.get("eventDelay");
		String sessionTimeoutStr = params.get("sessionTimeout");

		if (filePath == null || delayStr == null || sessionTimeoutStr == null) {
			System.out.println("Job requires the --filePath, --sessionTimeout and --eventDelay option to be specified.");
		} else {
			int delay = Integer.parseInt(delayStr);
			int sessionTimeout = Integer.parseInt(sessionTimeoutStr);
			StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

			env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

			final String[] inputKeys = {"_context_request_id", "payload:instance_type_id", "timestamp", "event_type", "publisher_id", "_context_user_name", "_context_project_name", "_context_tenant", "_context_project_id"};
			final String key = "_context_request_id";
			final String[] resultFields = {"_context_request_id", "timestamp"};
			final String errorKey = "event_type";
			final String errorRegex = "trove.+error";
			final String timestampPattern = "yyyy-MM-dd HH:mm:ss.SSSSSS";

			final Pattern errorPattern = Pattern.compile(errorRegex);

			DataStream<String> input = env.readTextFile(filePath);
			DataStream<JSONObject> jsonObjects = input.map(new MapFunction<String, JSONObject>() {
				@Override
				public JSONObject map(String s) throws Exception {
					return new JSONObject(s);
				}
			});

			Function<JSONObject, Long> timestampExtractor = new TimestampExtractorFunction("timestamp", timestampPattern);

			@SuppressWarnings("unchecked")
			DataStream<JSONObject> sessionMonitoring = JSONSessionMonitoring.createSessionMonitoring(
				jsonObjects, // input data set
				inputKeys, // json elements to keep from the input
				key, // key to group on
				new JSONObjectPredicateAnd( // session start element
					new JSONObjectPredicateMatchRegex("publisher_id", Pattern.compile("api.*novactl.*")),
					new JSONObjectPredicateMatchEquals<>("event_type", "compute.instance.update")),
				new JSONObjectPredicateMatchEquals<>("event_type", "compute.instance.create.end"), // session end element
				timestampExtractor,
				delay,	// maximum delay of events
				sessionTimeout, // session timeout
				new LatencyWindowFunction(resultFields), // create the latency from the first and last element of the session
				new LatencyTimeoutFunction(resultFields, sessionTimeout)
			);

			TypeInformation<JSONObject> jsonObjectTypeInformation = TypeExtractor.getForClass(JSONObject.class);

			DataStream<JSONObject> sessionTimeouts = sessionMonitoring.filter(new FilterFunction<JSONObject>() {
				@Override
				public boolean filter(JSONObject jsonObject) throws Exception {
					return jsonObject.has("sessionTimeout"); // we only want to keep the session timeouts
				}
			});

			DataStream<JSONObject> sessionAlerts = Alert.createAlert(
				sessionTimeouts, // filtered input for session timeouts
				"SessionAlerts", // name of operator
				5, // number of trigger events
				300000, // interval length in which the trigger events have to occur (milliseconds)
				new JSONObjectAlertFunction(
					"alert", // alert key
					"sessionTimeout", // alert value
					"timestamp", // timestamp key
					timestampPattern // timestamp pattern to generate
				),
				jsonObjectTypeInformation // output type information
			);

			DataStream<JSONObject> troveEvents = jsonObjects.filter(new FilterFunction<JSONObject>() {
				@Override
				public boolean filter(JSONObject jsonObject) throws Exception {
					return errorPattern.matcher(jsonObject.optString(errorKey)).matches();
				}
			});

			DataStream<JSONObject> troveAlerts = Alert.createAlert(
				troveEvents,
				"TroveAlerts",
				3,
				50000,
				new JSONObjectAlertFunction(
					"alert",
					"troveAlert",
					"timestamp",
					timestampPattern),
				jsonObjectTypeInformation
			);

			sessionAlerts.print();
			troveAlerts.print();

			env.execute("Execute timeout monitoring");
		}
	}
}
