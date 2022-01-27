package de.wuespace.telestion.extension.mongodb.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateUtils {

	public static String getISO8601StringForDate() {
		return getISO8601StringForDate(new Date());
	}

	public static String getISO8601StringForDate(Date date) {
		// TODO: Handle timezones better
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+02:00'", Locale.GERMANY);
		dateFormat.setTimeZone(TimeZone.getTimeZone("CET"));
		return dateFormat.format(date);
	}
}
