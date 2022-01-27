package de.wuespace.telestion.extension.mongodb.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.wuespace.telestion.api.message.JsonMessage;
import io.vertx.core.json.JsonObject;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public record DbResponse(@JsonProperty List<JsonObject> result) implements JsonMessage {

	public DbResponse() {
		this(Collections.emptyList());
	}
}
