package com.updater.ota.updater;

import android.content.Intent;
import android.util.Log;

import com.commonsware.cwac.wakeful.WakefulIntentService;

public class UpdateService extends WakefulIntentService {
    private static final String TAG = "UpdateService";

    public UpdateService(String name) {
        super(name);
    }

    public UpdateService() {
        super("UpdateOtaService");
    }

    @Override
    protected void doWakefulWork(Intent intent) {
       Log.d(TAG, "OTA Update service called!");
       UpdateChecker otaChecker = new UpdateChecker();
       otaChecker.execute(getBaseContext());
    }

}
