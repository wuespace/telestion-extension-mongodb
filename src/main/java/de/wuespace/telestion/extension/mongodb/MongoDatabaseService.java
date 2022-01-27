package de.wuespace.telestion.extension.mongodb;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.wuespace.telestion.api.message.JsonMessage;
import de.wuespace.telestion.api.verticle.TelestionConfiguration;
import de.wuespace.telestion.api.verticle.TelestionVerticle;
import de.wuespace.telestion.api.verticle.trait.WithEventBus;
import de.wuespace.telestion.extension.mongodb.message.DbRequest;
import de.wuespace.telestion.extension.mongodb.message.DbResponse;
import de.wuespace.telestion.services.message.Address;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;

import static de.wuespace.telestion.extension.mongodb.util.DateUtils.getISO8601StringForDate;

/**
 * MongoDatabaseService is a verticle which connects to a local running MongoDB-Database and listens for incoming
 * database requests to process.
 * <p>
 * Mongo specific:
 * Data is always saved in their exclusive collection which is always named after their Class.name.
 *
 * @author Jan Tischhöfer, Ludwig Richter
 */
// TODO: Each database implementation (currently only MongoDB) is written in its own DBClient,
// TODO: but listens to the same address for DBRequests. The address is the interface to the database implementation,
// TODO: so that the used DB can be replaced easily by spawning another DBClient.
@SuppressWarnings("unused")
public class MongoDatabaseService extends TelestionVerticle<MongoDatabaseService.Configuration>
		implements WithEventBus {

	/**
	 * Configuration for the {@link MongoDatabaseService} verticle.
	 * <br />
	 * <a href="https://vertx.io/docs/4.1.0/vertx-mongo-client/java/">
	 * https://vertx.io/docs/4.1.0/vertx-mongo-client/java/
	 * </a>
	 *
	 * @param host       The host the MongoDB instance is running. Defaults to {@code 127.0.0.1}.
	 * @param port       The port the MongoDB instance is listening on. Defaults to {@code 27017}.
	 * @param dbName     Name of the database in the MongoDB instance to use. Defaults to {@code daedalus2}.
	 * @param username   The username to authenticate. Default is {@code null}. (meaning no authentication required)
	 * @param password   The password to use to authenticate.
	 * @param dbPoolName The data source name in MongoDB which is shared between other MongoDB verticles.
	 */
	public record Configuration(
			@JsonProperty String host,
			@JsonProperty int port,
			@JsonProperty String dbName,
			@JsonProperty String username,
			@JsonProperty String password,
			@JsonProperty String dbPoolName
	) implements TelestionConfiguration {
		public Configuration() {
			this("127.0.0.1", 27017, "telestion-generic", null, null, "telestion-pool");
		}
	}

	@Override
	public void onStart() {
		var dbConfig = new JsonObject()
				.put("db_name", getConfig().dbName())
				.put("useObjectId", true)
				.put("host", getConfig().host())
				.put("port", getConfig().port())
				.put("username", getConfig().username())
				.put("password", getConfig().password());
		client = MongoClient.createShared(vertx, dbConfig, getConfig().dbPoolName());

		register(IN_SAVE, this::save, JsonMessage.class);
		register(IN_FIND, this::find, DbRequest.class);
		register(IN_AGGREGATE, this::aggregate, DbRequest.class);
	}

	@Override
	public void onStop(Promise<Void> stopPromise) {
		client.close().onComplete(stopPromise);
	}

	private MongoClient client;

	/**
	 * Save the received document to the database.
	 * If a MongoDB-ObjectId is specified data will be upserted, meaning if the id does not exist it will be inserted,
	 * otherwise it will be updated. Else it will be inserted with a new id.
	 * Additionally, the current date/time is added for future queries regarding date and time.
	 * If the save was successful the database looks for the newly saved document and publishes it to the database
	 * outgoing address concatenated with "/Class.name".
	 * Through this behaviour clients (e.g. GUI) can listen
	 * to the outgoing address of a specific data value and will always be provided with the most recent data.
	 */
	private void save(JsonMessage content, Message<Object> message) {
		var raw = content.json();
		var dateString = getISO8601StringForDate();
		raw.put("datetime", new JsonObject().put("$date", dateString));

		client.save(content.className(), raw).onComplete(result -> {
			if (result.succeeded()) {
				message.reply(true);
			} else {
				message.fail(500, result.cause().getMessage());
			}
		});
	}

	/**
	 * Find all requested entries in the MongoDB.
	 */
	private void find(DbRequest request, Message<Object> message) {
		client.findWithOptions(request.collection(), parseJsonQuery(request.query()), setFindOptions(request))
				.onComplete(result -> {
					if (result.succeeded()) {
						message.reply(new DbResponse(result.result()).json());
					} else {
						message.fail(500, result.cause().getMessage());
					}
				});
	}

	/**
	 * Aggregates data from MongoDB database.
	 */
	private void aggregate(DbRequest request, Message<Object> message) {
		var command = new JsonObject()
				.put("aggregate", request.collection())
				.put("pipeline", new JsonArray());
		command.getJsonArray("pipeline")
				.add(new JsonObject()
						.put("$match", parseJsonQuery(request.query())));
		// For each field in specified collection document you need to define the field and the operations
		// Outsource in helper function
		command.getJsonArray("pipeline")
				.add(new JsonObject()
						.put("$group", getGroupStageFromFields(request.aggregate())))
				.add(new JsonObject()
						.put("$project", new JsonObject()
								.put("_id", 0)
								.put("min", "$min")
								.put("avg", "$avg")
								.put("max", "$max")
								.put("last", "$last")
								.put("time", new JsonObject().put("$toLong", "$time"))))
				.add(new JsonObject()
						.put("$sort", new JsonObject()
								.put("datetime", 1) // TODO: Don't hard-code this
						)
				);
		command.put("cursor", new JsonObject());

		logger.info("Pipeline: " + command.getJsonArray("pipeline").toString());
		client.runCommand("aggregate", command).onComplete(result -> {
			if (result.succeeded()) {
				message.reply(result.result());
			} else {
				message.fail(500, result.cause().getMessage());
			}
		});
	}

	private JsonObject getGroupStageFromFields(String field) {
		return new JsonObject()
				.put("_id", "$datetime")
				// calculate avg/min/max for field
				.put("min", new JsonObject().put("$min", "$" + field))
				.put("avg", new JsonObject().put("$avg", "$" + field))
				.put("max", new JsonObject().put("$max", "$" + field))
				.put("last", new JsonObject().put("$last", "$" + field))
				.put("time", new JsonObject().put("$last", "$datetime"));
	}

	/**
	 * Helper function to set the {@link FindOptions}
	 * for the {@link MongoClient#findWithOptions(String, JsonObject, FindOptions)}
	 * from a database request.
	 *
	 * @param request the database request with the important information
	 * @return new find options for the MongoDB request
	 */
	private FindOptions setFindOptions(DbRequest request) {
		return setFindOptions(request.fields(), request.sort(), request.limit(), request.skip());
	}

	/**
	 * Helper function to set the {@link FindOptions}
	 * for the {@link MongoClient#findWithOptions(String, JsonObject, FindOptions)}.
	 *
	 * @param limit Limits the amount of returned entries. -1 equals all entries found.
	 * @param skip  Specifies if and how many entries should be skipped.
	 * @return {@link FindOptions} for the MongoClient.
	 */
	private FindOptions setFindOptions(List<String> fields, List<String> sort, int limit, int skip) {
		return setFindOptions(fields, sort).setLimit(limit).setSkip(skip);
	}

	/**
	 * Helper function to set the {@link FindOptions}
	 * for the {@link MongoClient#findWithOptions(String, JsonObject, FindOptions)}.
	 *
	 * @param fields List of key Strings in the collection limiting the fields that should be returned.
	 * @param sort   List of key Strings that the returned data should be sorted by.
	 * @return {@link FindOptions} for the MongoClient.
	 */
	private FindOptions setFindOptions(List<String> fields, List<String> sort) {
		FindOptions findOptions = new FindOptions();
		if (!fields.isEmpty()) {
			JsonObject jsonFields = new JsonObject();
			fields.forEach(f -> jsonFields.put(f, true));
			findOptions.setFields(jsonFields);
		}
		if (!sort.isEmpty()) {
			JsonObject jsonSort = new JsonObject();
			sort.forEach(s -> jsonSort.put(s, -1));
			findOptions.setSort(jsonSort);
		}
		return findOptions;
	}

	/**
	 * Helper function to parse a query string to a {@link JsonObject}.
	 *
	 * @param query JSON String - "{"key":"value"}, ..."
	 * @return {@link JsonObject} query for
	 * {@link MongoClient#findWithOptions(String, JsonObject, FindOptions)}
	 */
	private JsonObject parseJsonQuery(String query) {
		if (query.isEmpty()) {
			return new JsonObject();
		}

		try {
			return new JsonObject(query);
		} catch (DecodeException e) {
			logger.error("No valid JSON String: ".concat(e.getMessage()).concat("\nReturning empty JsonObject."));
			return new JsonObject();
		}
	}

	private static final String IN_SAVE = Address.incoming(MongoDatabaseService.class, "save");
	private static final String IN_FIND = Address.incoming(MongoDatabaseService.class, "find");
	private static final String IN_AGGREGATE = Address.incoming(MongoDatabaseService.class, "aggregate");
}
