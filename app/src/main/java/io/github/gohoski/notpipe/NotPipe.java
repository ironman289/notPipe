package io.github.gohoski.notpipe;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import io.github.gohoski.notpipe.api.Manager;
import io.github.gohoski.notpipe.config.ConfigManager;

public class NotPipe extends Application {
    public static final int SDK = Integer.parseInt(Build.VERSION.SDK);
    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = this;
        
        ConfigManager.init(this);
        ConfigManager.getInstance().ensureInstancesConfigured();
        Manager.init();
        SSLDisabler.disableSSLCertificateChecking();

        // Start TelegramService
        Intent tgIntent = new Intent(this, TelegramService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(tgIntent);
        } else {
            startService(tgIntent);
        }
    }

    public static Context getAppContext() {
        return appContext;
    }
}
