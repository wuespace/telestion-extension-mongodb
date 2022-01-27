package de.wuespace.telestion.extension.mongodb;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.wuespace.telestion.api.verticle.TelestionConfiguration;
import de.wuespace.telestion.api.verticle.TelestionVerticle;
import de.wuespace.telestion.api.verticle.trait.WithEventBus;
import de.wuespace.telestion.api.verticle.trait.WithTiming;
import de.wuespace.telestion.extension.mongodb.message.DbRequest;
import de.wuespace.telestion.services.message.Address;
import io.vertx.core.json.JsonObject;

import java.util.Collections;
import java.util.List;

import static de.wuespace.telestion.extension.mongodb.util.DurationUtils.fromRate;

/**
 * Verticle periodically querying the database for preconfigured data,
 * publishing the results to a preconfigured outgoing address.
 *
 * @author Jan Tischhöfer, Ludwig Richter
 * @version 07-05-2021
 */
@SuppressWarnings("unused")
public final class PeriodicDataPublisher extends TelestionVerticle<PeriodicDataPublisher.Configuration>
		implements WithTiming, WithEventBus {

	public record Configuration(
			@JsonProperty String collection,
			@JsonProperty String query,
			@JsonProperty List<String> fields,
			@JsonProperty List<String> sort,
			@JsonProperty int rate,
			@JsonProperty String outAddress,
			@JsonProperty String aggregate
	) implements TelestionConfiguration {
		public Configuration() {
			this("", "", Collections.emptyList(), Collections.emptyList(), 0, "", "");
		}
	}

	@Override
	public void onStart() {
		var delay = fromRate(getConfig().rate());
		logger.info("Periodic delay is: {}", delay);

		dbRequest = getDbRequestFromConfig();
		interval(delay, this::databaseRequest);
	}

	private DbRequest dbRequest;
	private String timeOfLastDataSet = null;

	private final String db = Address.incoming(MongoDatabaseService.class, "find");
	private final String agg = Address.incoming(MongoDatabaseService.class, "aggregate");

	/**
	 * Function to request the preconfigured DbRequest.
	 */
	private void databaseRequest(Long timerId) {
		if (timeOfLastDataSet != null) {
			var dateQuery = new JsonObject()
					.put("datetime", new JsonObject().put("$gt", new JsonObject().put("$date", timeOfLastDataSet)));
			dbRequest = getDbRequestFromConfig(dateQuery.toString());
		}

		this.<JsonObject>request(agg, dbRequest)
				.onFailure(cause -> logger.error(cause.getMessage()))
				.onSuccess(message -> {
					var body = message.body();
					logger.info("Received body from request: {}", body.toString());
					var jArr = body.getJsonArray("result");
					// Set timeOfLastDataSet to the datetime of the last received data
					timeOfLastDataSet = jArr
							.getJsonObject(jArr.size() - 1)
							.getJsonObject("datetime")
							.getString("$date");
					publish(getConfig().outAddress(), jArr);
				});
	}

	/**
	 * Function to create DbRequest from config.
	 *
	 * @return a new database request created from the current configuration
	 */
	private DbRequest getDbRequestFromConfig() {
		// TODO: Make parameters optional and config easier.
		return new DbRequest(
				getConfig().collection(),
				getConfig().query(),
				getConfig().fields(),
				getConfig().sort(),
				-1,
				0,
				getConfig().aggregate()
		);
	}

	/**
	 * Function to create DbRequest from config with a new query containing e.g. the new last date/time.
	 *
	 * @param query new query in JSON String representation
	 * @return {@link DbRequest}
	 */
	private DbRequest getDbRequestFromConfig(String query) {
		return new DbRequest(
				getConfig().collection(),
				query,
				getConfig().fields(),
				getConfig().sort(),
				-1,
				0,
				getConfig().aggregate()
		);
	}
}
