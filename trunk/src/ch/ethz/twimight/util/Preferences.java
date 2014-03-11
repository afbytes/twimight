package ch.ethz.twimight.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Shared preference wrapper for easier use of resource strings as preference
 * keys.
 * 
 * @author Steven Meliopoulos
 * 
 */
public class Preferences {

	public static String getString(Context context, int preferenceKeyResId, String defaultValue) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String key = context.getString(preferenceKeyResId);
		return sharedPreferences.getString(key, defaultValue);
	}

	public static String getString(Context context, int preferenceKeyResId, int defaultValueResId) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String key = context.getString(preferenceKeyResId);
		String defaultValue = context.getString(defaultValueResId);
		return sharedPreferences.getString(key, defaultValue);
	}

	public static long getLong(Context context, int preferenceKeyResId, long defaultValue) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String key = context.getString(preferenceKeyResId);
		return sharedPreferences.getLong(key, defaultValue);
	}

	public static boolean getBoolean(Context context, int preferenceKeyResId, boolean defaultValue) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String key = context.getString(preferenceKeyResId);
		return sharedPreferences.getBoolean(key, defaultValue);
	}

	public static void update(Context context, int preferenceKeyResId, String value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String key = context.getString(preferenceKeyResId);
		sharedPreferences.edit().putString(key, value).commit();
	}

	public static void update(Context context, int preferenceKeyResId, long value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String key = context.getString(preferenceKeyResId);
		sharedPreferences.edit().putLong(key, value).commit();
	}

	public static void update(Context context, int preferenceKeyResId, boolean value) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String key = context.getString(preferenceKeyResId);
		sharedPreferences.edit().putBoolean(key, value).commit();
	}

	public static void remove(Context context, int preferenceKeyResId) {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		String key = context.getString(preferenceKeyResId);
		sharedPreferences.edit().remove(key).commit();
	}

}
