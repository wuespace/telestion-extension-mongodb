package de.wuespace.telestion.extension.mongodb;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.wuespace.telestion.api.message.JsonMessage;
import de.wuespace.telestion.api.verticle.TelestionConfiguration;
import de.wuespace.telestion.api.verticle.TelestionVerticle;
import de.wuespace.telestion.api.verticle.trait.WithEventBus;
import de.wuespace.telestion.extension.mongodb.message.DataOperation;
import de.wuespace.telestion.extension.mongodb.message.DataRequest;
import de.wuespace.telestion.extension.mongodb.message.DbRequest;
import de.wuespace.telestion.services.message.Address;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.util.Collections;
import java.util.Map;

/**
 * DataService is a verticle which is the interface to a underlying database implementation.
 * All data requests should come to the DataService and will be parsed and executed.
 * TODO: DataOperations like Integrate, Differentiate, Offset, Sum, ...
 * TODO: MongoDB Queries explanation and implementation in fetchLatestData.
 */
@SuppressWarnings("unused")
public class DataService extends TelestionVerticle<DataService.Configuration> implements WithEventBus {
	public record Configuration(
			@JsonProperty Map<String, DataOperation> dataOperationMap
	) implements TelestionConfiguration {
		public Configuration() {
			this(Collections.emptyMap());
		}
	}

	@Override
	public void onStart() {
		register(inFind, this::dataRequestDispatcher, DataRequest.class);
		register(inSave, message -> request(dbSave, message)
				.onFailure(cause -> message.fail(500, cause.getMessage()))
				.onSuccess(message::reply));
	}

	/**
	 * DataService Eventbus Addresses.
	 */
	private final String inSave = Address.incoming(DataService.class, "save");
	private final String inFind = Address.incoming(DataService.class, "find");
	private final String dbSave = Address.incoming(MongoDatabaseService.class, "save");
	private final String dbFind = Address.incoming(MongoDatabaseService.class, "find");

	/**
	 * Parse and dispatch incoming DataRequests.
	 */
	private void dataRequestDispatcher(DataRequest request, Message<JsonObject> message) {
		var latestData = fetchLatestData(request.collection(), request.query());

		// manipulate latest data if request is more specific
		if (!request.collection().isEmpty()) {
			latestData = latestData.compose(result -> {
				var dataOperation = new DataOperation(
						new JsonObject().put("data", result),
						request.operationParams());

				return applyManipulation(request.operation(), dataOperation);
			});
		}

		latestData
				.onFailure(cause -> message.fail(500, cause.getMessage()))
				.onSuccess(message::reply);
	}

	/**
	 * Request data from another verticle and handle the result of the request.
	 *
	 * @param address Address String of the desired verticle.
	 * @param message Object to send to the desired verticle.
	 */
	private Future<JsonObject> requestResultHandler(String address, JsonMessage message) {
		return request(address, message).map(response -> new JsonObject().put("data", response.body()));
	}

	/**
	 * Method to fetch the latest data of a specified data type.
	 *
	 * @param collection Determines from which collection data should be fetched.
	 * @param query      MongoDB query, can be empty JsonObject if no specific query is needed.
	 */
	private Future<JsonObject> fetchLatestData(String collection, String query) {
		DbRequest dbRequest = new DbRequest(collection, query);
		return requestResultHandler(dbFind, dbRequest);
	}

	/**
	 * Apply data operation to fetched data.
	 *
	 * @param dataOperation Determines which manipulation should be applied.
	 */
	private Future<JsonObject> applyManipulation(String operationAddress, DataOperation dataOperation) {
		return requestResultHandler(operationAddress, dataOperation);
	}
}
