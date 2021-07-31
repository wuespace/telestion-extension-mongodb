package de.wuespace.telestion.extension.mongodb;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.wuespace.telestion.api.message.JsonMessage;
import io.vertx.core.json.JsonObject;

public record DataOperation(
		@JsonProperty JsonObject data,
		@JsonProperty JsonObject params) implements JsonMessage {
			private DataOperation() {
				this(new JsonObject(), new JsonObject());
			}
}
