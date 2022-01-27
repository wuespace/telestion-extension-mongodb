package de.wuespace.telestion.extension.mongodb.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.wuespace.telestion.api.message.JsonMessage;
import io.vertx.core.json.JsonObject;

@SuppressWarnings("unused")
public record DataOperation(
		@JsonProperty JsonObject data,
		@JsonProperty JsonObject params
) implements JsonMessage {

	public DataOperation() {
		this(new JsonObject(), new JsonObject());
	}
}
