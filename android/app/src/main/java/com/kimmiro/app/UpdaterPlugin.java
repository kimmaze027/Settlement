package com.kimmiro.app;

import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * In-app self-update for sideloaded distribution via GitHub Releases.
 *
 * There is no Play Store, so Play's in-app update API is unavailable. This plugin:
 *   1. reports the installed app version (BuildConfig.VERSION_NAME),
 *   2. downloads the latest APK from a URL (streaming, with progress events),
 *   3. hands the file to Android's package installer via a FileProvider URI.
 *
 * Uses java.net.HttpURLConnection (no extra dependency). The GitHub release download
 * URL redirects https -> https, which HttpURLConnection follows by default.
 *
 * Requires <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
 * and an <external-files-path> entry in res/xml/file_paths.xml.
 */
@CapacitorPlugin(name = "Updater")
public class UpdaterPlugin extends Plugin {

    @PluginMethod
    public void getVersion(PluginCall call) {
        JSObject ret = new JSObject();
        try {
            android.content.pm.PackageInfo pi = getContext().getPackageManager()
                    .getPackageInfo(getContext().getPackageName(), 0);
            ret.put("version", pi.versionName != null ? pi.versionName : "");
            ret.put("code", pi.getLongVersionCode());
        } catch (Exception e) {
            call.reject("version unavailable: " + e.getMessage());
            return;
        }
        call.resolve(ret);
    }
    @PluginMethod
    public void downloadAndInstall(PluginCall call) {
        final String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("url required");
            return;
        }

        final File outFile = new File(getContext().getExternalFilesDir(null), "settlement-update.apk");
        // Network I/O must not block the WebView thread; resolve/reject/notify are thread-safe.
        new Thread(() -> {
            FileOutputStream fos = null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                conn.connect();
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    call.reject("HTTP " + code);
                    return;
                }
                long total = conn.getContentLength();
                InputStream is = conn.getInputStream();
                fos = new FileOutputStream(outFile);
                byte[] buf = new byte[8192];
                long done = 0;
                int n;
                int lastPct = -1;
                while ((n = is.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        int pct = (int) (done * 100 / total);
                        if (pct != lastPct) {
                            lastPct = pct;
                            emitProgress(pct);
                        }
                    }
                }
                fos.flush();
                installApk(outFile);
                call.resolve();
            } catch (Exception e) {
                call.reject("download failed: " + e.getMessage());
            } finally {
                if (fos != null) {
                    try { fos.close(); } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private void emitProgress(int percent) {
        JSObject o = new JSObject();
        o.put("percent", percent);
        notifyListeners("updateProgress", o);
    }

    private void installApk(File apk) {
        // ACTION_VIEW + package-archive MIME is the current way to launch the installer
        // (ACTION_INSTALL_PACKAGE was deprecated in API 26). FLAG_ACTIVITY_NEW_TASK is
        // required because we launch from an application context.
        Uri uri = FileProvider.getUriForFile(
                getContext(),
                getContext().getPackageName() + ".fileprovider",
                apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }
}
