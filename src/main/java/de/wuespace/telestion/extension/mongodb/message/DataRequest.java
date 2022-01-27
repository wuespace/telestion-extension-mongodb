package de.wuespace.telestion.extension.mongodb.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.wuespace.telestion.api.message.JsonMessage;
import io.vertx.core.json.JsonObject;

@SuppressWarnings("unused")
public record DataRequest(
		@JsonProperty String collection,
		@JsonProperty String query,
		@JsonProperty String operation,
		@JsonProperty JsonObject operationParams
) implements JsonMessage {

	public DataRequest() {
		this("", "", "", new JsonObject());
	}
}
