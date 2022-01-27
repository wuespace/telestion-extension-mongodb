package de.wuespace.telestion.extension.mongodb;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.wuespace.telestion.api.verticle.TelestionConfiguration;
import de.wuespace.telestion.api.verticle.TelestionVerticle;
import de.wuespace.telestion.api.verticle.trait.WithEventBus;
import de.wuespace.telestion.services.message.Address;

import java.util.Collections;
import java.util.List;

/**
 * Listener that collects all incoming data configured in listeningAddresses and redirects them to be saved to the
 * MongoDatabaseService.
 *
 * @author Jan Tischhöfer, Ludwig Richter
 */
@SuppressWarnings("unused")
public class DataListener extends TelestionVerticle<DataListener.Configuration> implements WithEventBus {

	public record Configuration(
			@JsonProperty List<String> listeningAddresses
	) implements TelestionConfiguration {
		public Configuration() {
			this(Collections.emptyList());
		}
	}

	@Override
	public void onStart() {
		getConfig().listeningAddresses().forEach(
				address -> register(address, message -> publish(SAVE_ADDRESS, message.body()))
		);
	}

	/**
	 * Mongo Database Service save address.
	 */
	private static final String SAVE_ADDRESS = Address.incoming(MongoDatabaseService.class, "save");
}
