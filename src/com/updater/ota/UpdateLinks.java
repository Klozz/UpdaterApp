package com.updater.ota;

import com.updater.ota.R;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class UpdateLinks extends Fragment {

    private LinearLayout mDownload;
    private TextView mDownloadTitle;
    private TextView mDownloadSummary;

    private String mStrFileNameNew;
    private String mStrFileURLNew;
    private String mStrCurFile;

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
        mDownload.setOnClickListener(mActionLayouts);

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
            Toast.makeText(getActivity().getBaseContext(), getString(R.string.system_prop_error),
                    Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

        SharedPreferences shPrefs = getActivity().getSharedPreferences("UpdateChecker", 0);
        mStrFileNameNew = shPrefs.getString("Filename", "");
        mStrFileURLNew = shPrefs.getString("DownloadUrl", "");

        updateView();
    }

    private void launchUrl(String url) {
        Uri uriUrl = Uri.parse(url);
        Intent urlIntent = new Intent(Intent.ACTION_VIEW, uriUrl);
        urlIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getActivity().startActivity(urlIntent);
    }

    public void updateView() {
        if (!mStrFileNameNew.equals("") && !mStrFileURLNew.equals("")) {
            mDownloadTitle.setTextColor(Color.GREEN);
            mDownloadSummary.setTextColor(Color.GREEN);
            mDownloadSummary.setText(getString(R.string.short_cut_download_summary_update_available));
        }
    }
}
