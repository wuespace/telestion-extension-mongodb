package de.wuespace.telestion.extension.mongodb.util;

import java.time.Duration;

public class DurationUtils {

	/**
	 * Helper function to turn rate into milliseconds.
	 *
	 * @param rate the desired data rate times per second
	 * @return a duration in (1/rate)
	 */
	public static Duration fromRate(int rate) {
		return Duration.ofMillis((long) ((1.0 / rate) * 1000.5));
	}
}
