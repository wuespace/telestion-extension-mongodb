package de.wuespace.telestion.extension.mongodb;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.wuespace.telestion.api.message.JsonMessage;
import io.vertx.core.json.JsonObject;

public record DataRequest(
		@JsonProperty String collection,
		@JsonProperty String query,
		@JsonProperty String operation,
		@JsonProperty JsonObject operationParams) implements JsonMessage {
			private DataRequest() {
				this("", "", "", new JsonObject());
			}
}
