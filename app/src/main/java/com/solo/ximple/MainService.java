package com.solo.ximple;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Android App 隐藏图标(Android 10除外)并隐式启动
 * https://blog.csdn.net/u014460354/article/details/107590621
 *
 *【Android刨坑】8.0+高版本适配
 * https://www.jianshu.com/p/3172cce1a894
 */
public class MainService extends Service {

    private static String TAG = "solo_ProxyLogger";
    private static int NOTIFICATION_ID = 10086;
    private static String NOTIFICATION_CHANNEL_ID = "" + NOTIFICATION_ID;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String channelId = getString(R.string.app_name);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                    .setAutoCancel(true)
                    .setContentTitle("Keepalive")
                    .setContentText("Connection")
                    .setContentIntent(pendingIntent);

            NotificationManager notificationManager =	(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if(notificationManager != null) {
                int importance = NotificationManager.IMPORTANCE_DEFAULT;
                NotificationChannel notificationChannel = new NotificationChannel(channelId, NOTIFICATION_CHANNEL_ID, importance);
                notificationChannel.enableLights(false);
                notificationChannel.enableVibration(false);
                notificationManager.createNotificationChannel(notificationChannel);
                builder.setChannelId(channelId);
            } else{
                Log.e(TAG, "Unable to retrieve notification Manager service");
            }

            startForeground(NOTIFICATION_ID, builder.build());
        }

        String Did = getDeviceId(this);
        Log.i(TAG, "onCreate: GetDeviceId:" + Did);
        new Thread(new Runnable() {
            @Override
            public void run() {
                ProxyMain.AppWithDeviceId(Did);
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        ProxyMain.AppStop();
        Log.i(TAG, "onServiceDestroy");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true); //true will remove notification
        }
        super.onDestroy();
    }

    /**
     * 获取deviceId(手机唯一的标识)
     *
     * @param context Phase Error
     * @return
     */
    public static String getDeviceId(Context context) {
        String deviceId;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } else {
            try {
                deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                return deviceId;
            }
            catch (Exception e)
            {}

            final TelephonyManager mTelephony = (TelephonyManager) context.getSystemService(TELEPHONY_SERVICE);
            try {
                context.getClass().getMethod("checkSelfPermission", String.class);
                if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    return "";
                }
            }
            catch (Exception e) {
                Log.e(TAG, "getDeviceId: " + e.getMessage());
            }
            assert mTelephony != null;
            try {
                if (mTelephony.getDeviceId() != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        deviceId = mTelephony.getImei();
                    } else {
                        deviceId = mTelephony.getDeviceId();
                    }
                } else {
                    deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                }
            }
            catch(Exception e) {
                return "";
            }
        }
        return deviceId;
    }

}
