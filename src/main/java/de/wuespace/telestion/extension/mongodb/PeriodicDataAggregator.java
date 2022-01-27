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
import java.util.Date;
import java.util.Objects;

import static de.wuespace.telestion.extension.mongodb.util.DateUtils.getISO8601StringForDate;
import static de.wuespace.telestion.extension.mongodb.util.DurationUtils.fromRate;

/**
 * Verticle periodically querying the database for preconfigured data,
 * publishing the results to a preconfigured outgoing address.
 *
 * @author Jan Tischhöfer, Ludwig Richter
 */
@SuppressWarnings("unused")
public class PeriodicDataAggregator extends TelestionVerticle<PeriodicDataAggregator.Configuration>
		implements WithTiming, WithEventBus {

	public record Configuration(
			@JsonProperty String collection,
			@JsonProperty String field,
			@JsonProperty int rate,
			@JsonProperty String outAddress
	) implements TelestionConfiguration {
	}

	@Override
	public void onStart() {
		var delay = fromRate(getConfig().rate());
		logger.info("Periodic delay is: {}", delay);

		timeOfLastDataSet = getISO8601StringForDate();
		interval(delay, this::databaseRequest);
	}

	private String timeOfLastDataSet;

	/**
	 * Function to request the preconfigured DbRequest.
	 */
	private void databaseRequest(Long timerId) {
		logger.debug("Executing aggregation request...");

		// build query to aggregate data newer than last published dataset
		var dateQuery = new JsonObject()
				.put("datetime", new JsonObject().put("$gt", new JsonObject().put("$date", timeOfLastDataSet)));
		var dbRequest = new DbRequest(getConfig().collection(), dateQuery.toString(), getConfig().field());

		// request on database verticle
		this.<JsonObject>request(IN_AGGREGATE, dbRequest)
				.onFailure(cause -> logger.error(cause.getMessage()))
				.onSuccess(message -> {
					var body = message.body();
					logger.info("Received body from request: {}", body.toString());
					if (Objects.nonNull(body)) {
						var jArr = body.getJsonObject("cursor").getJsonArray("firstBatch");
						if (!jArr.isEmpty()) {
							// Set timeOfLastDataSet to the datetime of the last received data
							long unixTs = Long.parseLong(jArr.getJsonObject(jArr.size() - 1).getString("time"));
							timeOfLastDataSet = getISO8601StringForDate(new Date(unixTs));
							publish(getConfig().outAddress(), jArr);
						}
					}
				});
	}

	/**
	 * Function to create DbRequest from config.
	 *
	 * @return the configured database request
	 */
	private DbRequest getDbRequestFromConfig() {
		// TODO: Make parameters optional and config easier.
		return new DbRequest(
				getConfig().collection(),
				"",
				Collections.emptyList(),
				Collections.emptyList(),
				-1,
				0,
				getConfig().field()
		);
	}

	/**
	 * Function to create DbRequest from config with a new query containing e.g. the new last date/time.
	 *
	 * @param query new query in JSON String representation
	 * @return the configured database request with the specified query
	 */
	private DbRequest getDbRequestFromConfig(String query) {
		return new DbRequest(
				getConfig().collection(),
				query,
				Collections.emptyList(),
				Collections.emptyList(),
				-1,
				0,
				getConfig().field()
		);
	}

	/**
	 * Mongo Database Service aggregation address.
	 */
	private static final String IN_AGGREGATE = Address.incoming(MongoDatabaseService.class, "aggregate");
}
