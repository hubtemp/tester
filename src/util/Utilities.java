package util;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Utilities {

	public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss.SSS").withZone(ZoneId.of("Europe/Riga"));
	public static final Locale LOCALE = new Locale("lv", "LV");

	public static String str(String s) {
		return str(s, "");
	}

	public static String str(String s, String substitute) {
		return isEmpty(s) ? substitute : s;
	}

	public static boolean isEmpty(String s) {
		return s == null || s.isEmpty();
	}

	/**
	 * Returns current date & time of zone "Europe/Riga"
	 *
	 * @return date & time of zone "Europe/Riga"
	 */
	public static String getCurrentDateTime() {
		return DATE_TIME_FORMATTER.format(Instant.now());
	}

	/**
	 * Formats and outputs passed log message to System.err.println. Also works like String.format();
	 *
	 * @param message log text
	 * @param params objects, if message formatting is needed
	 */
	public static void logError(String message, Object... params) {
		System.err.println(getLogMessage(message, params));
	}

	/**
	 * Formats and outputs passed log message to System.out.println. Also works like String.format();
	 *
	 * @param message log text
	 * @param params objects, if message formatting is needed
	 */
	public static void logInfo(String message, Object... params) {
		System.out.println(getLogMessage(message, params));
	}

	/**
	 * Formats message for logging (adds current date & time). Also works like String.format().
	 *
	 * @param message log text
	 * @param params objects, if message formatting is needed
	 * @return string formatted log message
	 */
	public static String getLogMessage(String message, Object... params) {
		if (params == null || params.length < 1) {
			return String.format(LOCALE, "[%s] %s", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss.SSS")), message);
		}
		Object[] newParams = new Object[params.length + 1];
		newParams[0] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss.SSS"));
		System.arraycopy(params, 0, newParams, 1, params.length);
		return String.format(LOCALE, "[%s] " + message, newParams);
	}

	/**
	 * Calculates and returns duration as millis between passed start nanoTime and this method call.
	 *
	 * @param start duration start nanoTime
	 * @return string duration between start and this method call time as millis
	 */
	public static String getDuration(long start) {
		return formatDuration(System.nanoTime() - start);
	}

	/**
	 * Calculates and returns duration as millis between passed start and end nanoTime.
	 *
	 * @param start duration start nanoTime
	 * @param end duration end nanoTime
	 * @return string duration between start and end time as millis
	 */
	public static String getDuration(long start, long end) {
		return formatDuration(end - start);
	}

	/**
	 * Calculates and returns duration as millis from passed nanoTime.
	 *
	 * @param duration duration nanoTime
	 * @return string duration as millis
	 */
	public static String formatDuration(long duration) {
		return String.format(LOCALE, "%.2f", duration / 1000000d);
	}

	/**
	 * Using passed class instance, retrieves all class variables which are not null/empty and return as a String.
	 *
	 * @param instance class instance
	 * @return String of all class non-null field values
	 */
	public static String getInstanceValues(Object instance) {
		return getInstanceValues(instance, false);
	}

	/**
	 * Using passed class instance, retrieves all class variables which are not null and return as a String.
	 *
	 * @param instance class instance
	 * @param showEmptyValues - include/exclude elements with empty values
	 * @return String of all class non-null field values
	 */
	public static String getInstanceValues(Object instance, boolean showEmptyValues) {
		StringBuilder sb = new StringBuilder(instance.getClass().getSimpleName()).append(" instance:\n");
		for (Field field : instance.getClass().getDeclaredFields()) {
			try {
				Object value = field.get(instance);
				if (value != null && (showEmptyValues || !"".equals(value))) {
					String fieldType = field.getGenericType().getTypeName();
					fieldType = fieldType.substring(fieldType.lastIndexOf(".") + 1);
					sb.append("\t(").append(fieldType).append(") ").append(field.getName()).append("=").append(value).append("\n");
				}
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
		}
		return sb.toString();
	}

	/**
	 * Returns file name without extension from Path object.
	 *
	 * @param path Path object
	 * @return file name without extension
	 */
	public static String getFileNameWithoutExtension(Path path) {
		String fileName = path.getFileName().toString();
		int pos = fileName.lastIndexOf(".");
		if (pos > 0) {
			fileName = fileName.substring(0, pos);
		}
		return fileName;
	}

}
