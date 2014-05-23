package com.updater.ota;

import com.updater.ota.R;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.updater.ota.updater.UpdateChecker;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateLinks extends Fragment {

    private static final String TAG = "UpdateLinks";

    private static final String PERMISSION_ACCESS_CACHE_FILESYSTEM = "android.permission.ACCESS_CACHE_FILESYSTEM";
    private static final String PERMISSION_REBOOT = "android.permission.REBOOT";

    private LinearLayout mDownload;
    private TextView mDownloadTitle;
    private TextView mDownloadSummary;
    private ProgressBar mProgressBar;

    private String mStrFileNameNew;
    private String mStrFileURLNew;
    private String mStrIsUpToDate;
    private String mStrCurFile;

    private DownloadRomTask dTask;
    private boolean mDownloadSuccess = false;

    private Context mContext;

    public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.update_ota_links, container, false);
        return view;
    }

    private final View.OnClickListener mActionLayouts = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v == mDownload) {
                if (mStrFileURLNew != null
                    && mStrFileURLNew != "") {
                    launchUrl(mStrFileURLNew);
                }
            }
        }
    };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mDownload = (LinearLayout) getView().findViewById(R.id.short_cut_download);
        mDownloadTitle = (TextView) getView().findViewById(R.id.short_cut_download_title);
        mDownloadSummary = (TextView) getView().findViewById(R.id.short_cut_download_summary);
        mProgressBar = (ProgressBar) getView().findViewById(R.id.short_cut_download_progress);
        mDownload.setOnClickListener(mActionLayouts);
        mContext = getActivity().getBaseContext();
        dTask = new DownloadRomTask(mContext);

        try {
            FileInputStream fstream = new FileInputStream("/system/build.prop");
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;
            while ((strLine = br.readLine()) != null) {
                String[] line = strLine.split("=");
                if (line[0].equals("ro.modversion")) {
                    mStrCurFile = line[1];
                }
            }
            in.close();
        } catch (Exception e) {
            Toast.makeText(mContext, getString(R.string.system_prop_error),
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        SharedPreferences shPrefs = getActivity().getSharedPreferences("UpdateChecker", 0);
        mStrIsUpToDate = shPrefs.getString("isUpToDate", "");
        mStrFileNameNew = shPrefs.getString("Filename", "");
        mStrFileURLNew = shPrefs.getString("DownloadUrl", "");

        updateView();
    }

    private class DownloadRomTask extends AsyncTask<String, Integer, String> {

        private Context context;

        public DownloadRomTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(String... sUrl) {
            // take CPU lock to prevent CPU from going off if the user
            // presses the power button during download
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    getClass().getName());
            wl.acquire();

            try {
                InputStream input = null;
                OutputStream output = null;
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(sUrl[0]);
                    Log.i(TAG, "ROM URL: " + sUrl[0]);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    // expect HTTP 200 OK, so we don't mistakenly save error report
                    // instead of the file
                    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        return "Server returned HTTP " + connection.getResponseCode()
                                + " " + connection.getResponseMessage();
                    }
                    // this will be useful to display download percentage
                    // might be -1: server did not report the length
                    int fileLength = connection.getContentLength();

                    // download the file
                    input = connection.getInputStream();
                    output = new FileOutputStream("/sdcard/UpdateOTA/" + mStrFileNameNew);
                    Log.i(TAG, "ROM Save Location: " + "/sdcard/UpdateOTA/" + mStrFileNameNew);

                    byte data[] = new byte[65536];
                    long total = 0;
                    int count;
                    while ((count = input.read(data)) != -1) {
                        // allow canceling with back button
                        if (isCancelled()) {
                            return null;
                        }
                        total += count;
                        // publishing the progress....
                        if (fileLength > 0) { // only if total length is known
                            publishProgress((int) (total * 100 / fileLength));
                        }
                        output.write(data, 0, count);
                    }
                } catch (Exception e) {
                    return e.toString();
                } finally {
                    try {
                        if (output != null) {
                            output.close();
                        }
                        if (input != null) {
                            input.close();
                        }
                    } catch (IOException ignored) { }

                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            } finally {
                wl.release();
            }
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.i(TAG, "Starting download of ROM");
            Toast.makeText(context, "Download Started", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            // if we get here, length is known, now set indeterminate to false
            mDownloadSummary.setText("Downloading ROM " + String.valueOf(progress[0]) + "%");
            mProgressBar.setVisibility(ProgressBar.VISIBLE);
            mProgressBar.setIndeterminate(false);
            mProgressBar.setMax(100);
            mProgressBar.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                mDownloadSuccess = false;
                Toast.makeText(context,"Download error: " + result, Toast.LENGTH_LONG).show();
                Log.w(TAG, "ROM Download Failed: " + result);
                mProgressBar.setProgress(0);
                UpdateChecker otaChecker = new UpdateChecker();
                otaChecker.execute(context);
            } else {
                Log.w(TAG, "ROM Download Completed: " + result);
                mDownloadSuccess = true;
                mDownloadTitle.setTextColor(Color.YELLOW);
                mDownloadTitle.setText(getString(R.string.short_cut_flash_title));
                mDownloadSummary.setTextColor(Color.YELLOW);
                mDownloadSummary.setText(getString(R.string.reboot));
            }
        }
    }

    private void launchUrl(String url) {
        if (!mStrIsUpToDate.equals("")
                    && mStrIsUpToDate.equals("update")) {
            if (mDownloadSuccess) {
                flashUpdate();
            } else {
               dTask.execute(url);
            }
        }
    }

    private void flashErrorUpdate() {
        mDownloadTitle.setTextColor(Color.RED);
        mDownloadTitle.setText(getString(R.string.flash_error_title));
        mDownloadSummary.setTextColor(Color.RED);
        mDownloadSummary.setText(getString(R.string.flash_error));
    }

    @SuppressLint("SdCardPath")
    private void flashUpdate() {
        if (mContext.getPackageManager().checkPermission(PERMISSION_ACCESS_CACHE_FILESYSTEM,
                mContext.getPackageName()) != PackageManager.PERMISSION_GRANTED) {
            flashErrorUpdate();
            return;
        }

        if (mContext.getPackageManager().checkPermission(PERMISSION_REBOOT,
                mContext.getPackageName()) != PackageManager.PERMISSION_GRANTED) {
            flashErrorUpdate();
            return;
        }

        if ((mStrFileNameNew == null) || mStrFileNameNew.equals("")) {
            flashErrorUpdate();
            return;
        }

        try {
            // TWRP - OpenRecoveryScript - the recovery will find the correct
            // storage root for the ZIPs,
            // life is nice and easy.
            if ((mStrFileNameNew != null) && (!mStrFileNameNew.equals(""))) {
                FileOutputStream os = new FileOutputStream("/cache/recovery/openrecoveryscript",
                        false);
                try {
                    os.write(String.format("install %s\n", "/sdcard/UpdateOTA/" + mStrFileNameNew).getBytes("UTF-8"));
                    os.write(("wipe cache\n").getBytes("UTF-8"));
                } finally {
                    os.close();
                }
            }
            setPermissions("/cache/recovery/openrecoveryscript", 0644, Process.myUid(), 2001 /* AID_CACHE */);
            ((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).reboot("recovery");
        } catch (Exception e) {
        }
    }

    public void updateView() {
        if (!mStrIsUpToDate.equals("") && mStrIsUpToDate.equals("update")) {
            mDownloadTitle.setTextColor(Color.GREEN);
            mDownloadSummary.setTextColor(Color.GREEN);
            mDownloadSummary.setText(getString(R.string.short_cut_download_summary_update_available));
        }
    }

    /*
     * Using reflection voodoo instead calling the hidden class directly, to
     * dev/test outside of AOSP tree
     */
    private boolean setPermissions(String path, int mode, int uid, int gid) {
        try {
            Class<?> FileUtils = mContext.getClassLoader().loadClass("android.os.FileUtils");
            Method setPermissions = FileUtils.getDeclaredMethod("setPermissions", new Class[] {
                    String.class, int.class, int.class, int.class
            });
            return ((Integer) setPermissions.invoke(null, new Object[] {
                    path, Integer.valueOf(mode), Integer.valueOf(uid), Integer.valueOf(gid)
            }) == 0);
        } catch (Exception e) {
        }
        return false;
    }
}
