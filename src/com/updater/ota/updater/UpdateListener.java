package com.updater.ota.updater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;
import com.commonsware.cwac.wakeful.WakefulIntentService.AlarmListener;
import java.util.Calendar;

public class UpdateListener implements AlarmListener {
    private static final String TAG = "UpdateListener";
    private static final String LAST_INTERVAL = "lastInterval";
    public static long interval = AlarmManager.INTERVAL_HALF_DAY;

    @Override
    public void scheduleAlarms(AlarmManager mgr, PendingIntent pi, Context ctx) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());

        SharedPreferences prefs = ctx.getSharedPreferences(LAST_INTERVAL, 0);
        long value = prefs.getLong(LAST_INTERVAL,0);
        if (value == 0) {
            interval = AlarmManager.INTERVAL_HALF_DAY;
            prefs.edit().putLong(LAST_INTERVAL, interval).apply();
        } else if (value != 1) {
            interval = value;
        }

        mgr.setInexactRepeating(AlarmManager.RTC, calendar.getTimeInMillis(),
                    interval, pi);
    }

    @Override
    public void sendWakefulWork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        Log.d(TAG, "sendWakefulWork called!");
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            if (netInfo.getType() == ConnectivityManager.TYPE_MOBILE
                    || netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                Log.d(TAG, "We have internet, start update check directly now!");
                Intent backgroundIntent = new Intent(context, UpdateService.class);
                WakefulIntentService.sendWakefulWork(context, backgroundIntent);
            } else {
                Log.d(TAG, "We have no internet, enable ConnectivityReceiver!");
                ConnectivityReceiver.enableReceiver(context);
            }
        } else {
            Log.d(TAG, "We have no internet, enable ConnectivityReceiver!");
            ConnectivityReceiver.enableReceiver(context);
        }
    }

    @Override
    public long getMaxAge() {
        return (interval * 2);
    }

}
