package com.updater.ota.updater;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.webkit.URLUtil;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.updater.center.UpdateCenter;
import com.updater.ota.R;

public class UpdateChecker extends AsyncTask<Context, Integer, String> {
    private static final String TAG = "UpdateChecker";

    private static final int MSG_CREATE_DIALOG = 0;
    private static final int MSG_DISPLAY_MESSAGE = 1;
    private static final int MSG_SET_PROGRESS = 2;
    private static final int MSG_CLOSE_DIALOG = 3;

    private String strDevice, updateCurVer;
    private Context mContext;
    private int mId = 1000001;

    public ProgressDialog mProgressDialog;

    final Handler mHandler = new Handler() {

        public void createWaitDialog(){
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setTitle(mContext.getString(R.string.title_update));
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setMax(100);
            mProgressDialog.setMessage(mContext.getString(R.string.toast_text));
            mProgressDialog.show();
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CREATE_DIALOG:
                    createWaitDialog();
                    break;
                case MSG_DISPLAY_MESSAGE:
                    if (mProgressDialog == null) createWaitDialog();
                    if (mProgressDialog.isShowing()) {
                        mProgressDialog.setCancelable(true);
                        mProgressDialog.setProgress(mProgressDialog.getMax());
                        mProgressDialog.setMessage((String) msg.obj);
                    }
                    break;
                case MSG_SET_PROGRESS:
                    if (mProgressDialog != null) mProgressDialog.setProgress(((Integer) msg.obj));
                    break;
                case MSG_CLOSE_DIALOG:
                    if (mProgressDialog != null) mProgressDialog.dismiss();
                    break;
                default: // should never happen
                    break;
            }
        }
    };

    public void getDeviceTypeAndVersion() {
        try {
            FileInputStream fstream = new FileInputStream("/system/build.prop");
            BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
            String strLine;
            while ((strLine = br.readLine()) != null) {
                String[] line = strLine.split("=");
                if (line[0].equalsIgnoreCase("ro.product.device")) {
                    strDevice = line[1].trim();
                } else if (line[0].equalsIgnoreCase("ro.build.date.utc")) {
                    updateCurVer = line[1].trim();
                }
            }
            br.close();
        } catch (Exception e) {
            Log.e(TAG, "can't get device type and version", e);
        }
    }

    @Override
    protected String doInBackground(Context... arg) {
        mContext = arg[0];
        Message msg;
        if (mContext != null && mContext.toString().contains("UpdateCenter")) {
            msg = mHandler.obtainMessage(MSG_CREATE_DIALOG);
            mHandler.sendMessage(msg);
        }
        HttpURLConnection urlConnection = null;
        if (!connectivityAvailable(mContext)) return "connectivityNotAvailable";
        try {
            getDeviceTypeAndVersion();
            Log.d(TAG, "strDevice = "+strDevice+ "   updateCurVer = "+updateCurVer);
            if (strDevice == null || updateCurVer == null) return null;
            String newFileInfo = null;
            String newBuildDate = null;
            String newUpdateUrl = null;
            String newFileName = null;
            URL url = null;
            if (updateCurVer != null) {
                url = new URL(mContext.getString(R.string.xml_url));
            } else {
                return null;
            }
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();
            if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            newFileInfo = in.readLine();
            Log.d(TAG, "newFileInfo = "+ newFileInfo);
            if (newFileInfo == null) {
                return null;
            }
            String[] separated = newFileInfo.split(",");
            newBuildDate = separated[0];
            newFileName = separated[1];
            boolean upToDate = (Long.parseLong(updateCurVer) >= Long.parseLong(newBuildDate));
            if (!upToDate) {
                putDataInprefs(mContext, "Filename", newFileName);
                newUpdateUrl = mContext.getString(R.string.xml_url_rom) + "/" + newFileName;
                putDataInprefs(mContext, "DownloadUrl", newUpdateUrl);
                Log.d(TAG, "Filename = "+ newFileName + "   DownloadUrl = " + newUpdateUrl);
            } else {
                putDataInprefs(mContext, "Filename", "");
                putDataInprefs(mContext, "DownloadUrl", "");
            }
            return newUpdateUrl;
        } catch(Exception e) {
            Log.e(TAG, "error while connecting to server", e);
            return null;
        } finally {
            if (urlConnection !=null) urlConnection.disconnect();
        }
    }

    private void putDataInprefs(Context ctx, String entry, String value) {
        SharedPreferences prefs = ctx.getSharedPreferences(TAG, 0);
        String entryValue = prefs.getString(entry, "");
        if (!entryValue.equals(value)) {
            prefs.edit().putString(entry, value).apply();
        }
    }

    public static boolean connectivityAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return (netInfo != null && netInfo.isConnectedOrConnecting() && (netInfo.getType() == ConnectivityManager.TYPE_MOBILE ||
            netInfo.getType() == ConnectivityManager.TYPE_WIFI));
    }


    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        Log.d("\r\n"+TAG, "result= "+result+"\n context="+mContext.toString()+"\r\n");
        if (mContext != null && mContext.toString().contains("UpdateCenter")) {
            Message msg = mHandler.obtainMessage(MSG_CLOSE_DIALOG);
            mHandler.sendMessage(msg);
        } else if (result == null) {
            Log.d(TAG, "onPostExecute() - no new Update detected!" );
        } else {
            Log.d(TAG, "new Update available here: " + result);
            if (!URLUtil.isValidUrl(result)) {
                showInvalidLink();
            } else {
                showNotification();
            }
        }
    }

    private void showNotification() {
        Notification.Builder mBuilder = new Notification.Builder(mContext)
            .setContentTitle(mContext.getString(R.string.title_update))
            .setContentText(mContext.getString(R.string.notification_message))
            .setSmallIcon(R.drawable.ic_notification_updateota)
            .setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_updateota));

        Intent intent = new Intent(mContext, UpdateCenter.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final PendingIntent pendingIntent = PendingIntent.getActivity(mContext,
                    0, intent, PendingIntent.FLAG_ONE_SHOT);
        mBuilder.setContentIntent(pendingIntent);
        NotificationManager mNotificationManager =
            (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notif = mBuilder.build();
        notif.flags |= Notification.FLAG_AUTO_CANCEL;
        mNotificationManager.notify(mId, notif);
    }

    private void showInvalidLink() {
        if (mContext != null && mContext.toString().contains("UpdateCenter")) {
            Message msg = mHandler.obtainMessage(MSG_DISPLAY_MESSAGE, mContext.getString(R.string.bad_url));
            mHandler.sendMessage(msg);
        } else {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(mContext);
            alertDialog.setTitle(mContext.getString(R.string.title_update));
            alertDialog.setMessage(mContext.getString(R.string.bad_url));
            alertDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int which) {
                    dialog.cancel();
                }
            });
            alertDialog.show();
        }
    }
}
