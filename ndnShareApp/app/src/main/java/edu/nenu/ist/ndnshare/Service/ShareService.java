package edu.nenu.ist.ndnshare.Service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.support.v4.app.NotificationCompat;

import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.Name;
import net.named_data.jndn.OnData;
import net.named_data.jndn.OnTimeout;

import java.io.IOException;
import java.util.UUID;

import edu.nenu.ist.ndnshare.MainActivity;
import edu.nenu.ist.ndnshare.R;

public class ShareService extends AskService {
    private static final String TAG = "ShareService";

    //维持连接
    private static final int HEARTBEAT_TIMEOUT = 10000;

    public static final String ACTION_START = INTENT_PREFIX + "ACTION_START",
                                BCAST_RECEIVED_ASK = INTENT_PREFIX + "BCAST_RECEIVED_ASK",
                                EXTRA_MESSAGE = INTENT_PREFIX + "EXTRA_MESSAGE",
                                EXTRA_PREFIX = INTENT_PREFIX + "EXTRA_PREFIX",
                                ACTION_ASK_LIST = INTENT_PREFIX + "ACTION_ASK_LIST",
                                ACTION_ASK_EXACT_LIST = INTENT_PREFIX + "ACTION_ASK_EXACT_LIST",
                                ACTION_ASK_FUZZY_LIST = INTENT_PREFIX + "ACTION_ASK_FUZZY_LIST",
                                ACTION_ASK_FILE = INTENT_PREFIX + "ACTION_ASK_FILE",
                                ACTION_STOP = INTENT_PREFIX + "ACTION_STOP";

    @Override
    public void onCreate() {
        Log.d(TAG, "create ShareService ");
        Intent notificationIntent = new Intent(this, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        String channelId = "default";
        String CHANNEL_ONE_ID = "edu.nenu.ist.ndnshare";
        String CHANNEL_ONE_NAME = "Channel One";

        NotificationChannel notificationChannel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                    CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(notificationChannel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setChannelId(CHANNEL_ONE_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle(getText(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setContentIntent(pendingIntent)
                .setColor(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPrimary))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();

        startForeground(MainActivity.SERVICE_NOTIFICATION_ID, notification);

        initializeServiceIfNeeded(null,getString(R.string.data_prefix));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "received intent " + action);
            switch(action) {
                case ACTION_START:
                    break;
                case ACTION_ASK_EXACT_LIST:
                    String askExactname = intent.getStringExtra(EXTRA_MESSAGE);
                    Log.d(TAG, "received askExactList intent "+askExactname);
                    if (askExactname == null) {
                        raiseError("ACTION_ASK_LIST intent requires EXTRA_MESSAGE",
                                ErrorCode.OTHER_EXCEPTION);
                    } else {
                        askExactFileList(askExactname);
                    }
                    break;
                case ACTION_ASK_FUZZY_LIST:
                    String askFuzzyname = intent.getStringExtra(EXTRA_MESSAGE);
                    Log.d(TAG, "received askFuzzyList intent "+askFuzzyname);
                    if (askFuzzyname == null) {
                        raiseError("ACTION_ASK_LIST intent requires EXTRA_MESSAGE",
                                ErrorCode.OTHER_EXCEPTION);
                    } else {
                        askFuzzyFileList(askFuzzyname);
                    }
                    break;
                case ACTION_ASK_FILE:
                    String filename = intent.getStringExtra(EXTRA_MESSAGE);
                    String prefix = intent.getStringExtra(EXTRA_PREFIX);
                    Log.d(TAG, "received askFile intent "+ filename);
                    if (filename == null) {
                        raiseError("ACTION_ASK_FILE intent requires EXTRA_MESSAGE",
                                ErrorCode.OTHER_EXCEPTION);
                    } else {
                        askFile(filename, prefix);
                    }
                    break;
                case ACTION_STOP:
                    stopSelf();
                    break;
            }
        }

        return START_STICKY;
    }

    public void askExactFileList(String askname){
        initializeServiceIfNeeded(askname,getString(R.string.data_prefix));
        extraFileListPrefix = askname;
        askExactFileList = true;
    }

    public void askFuzzyFileList(String askname){
        initializeServiceIfNeeded(askname,getString(R.string.data_prefix));
        extraFileListPrefix = askname;
        askFuzzyFileList = true;
    }

    public void askFile(String filename , String prefix){
        initializeServiceIfNeeded(filename,getString(R.string.data_prefix));
        extraFilePrefix = filename;
        baseFilePrefix = prefix;
        askFile = true;
    }

    //prefix注册
    private void initializeServiceIfNeeded(String filename, final String prefix) {

        Log.d(TAG, "initializeServiceIfNeeded is run");

        if (!networkThreadIsRunning()) {

            String separator = "/",
                    randomString = getRandomStringForDataPrefix(),
                    dataPrefix = prefix + separator + randomString,
                    broadcastPrefix = getString(R.string.broadcast_base_prefix) + separator +
                            getString(R.string.search_prefix_component);

            initializeService(dataPrefix, broadcastPrefix);
        }
    }

    private String getRandomStringForDataPrefix() {
        return UUID.randomUUID().toString();
    }

    @Override
    protected void doApplicationSetup() {
        Log.d(TAG, "心跳判定");
        expressHeartbeatInterest();
    }

    private void expressHeartbeatInterest() {
        //Log.d(TAG, "(re)starting heartbeat timeout");
        expressTimeoutInterest(OnHeartBeatTimeout, HEARTBEAT_TIMEOUT,
                "error setting up heartbeat");
    }

    private final OnTimeout OnHeartBeatTimeout = new OnTimeout() {
        @Override
        public void onTimeout(Interest interest) {
            expressHeartbeatInterest();
        }
    };

    private Long expressTimeoutInterest(OnTimeout onTimeout, long lifetimeMillis, String errorMsg) {
        Interest timeout = new Interest(new Name("/timeout"));
        timeout.setInterestLifetimeMilliseconds(lifetimeMillis);
        try {
            return face.expressInterest(timeout, DummyOnData, onTimeout);
        } catch (IOException e) {
            raiseError(errorMsg, ErrorCode.NFD_PROBLEM, e);
            return null;
        }
    }

    private static final OnData DummyOnData = new OnData() {
        @Override
        public void onData(Interest interest, Data data) {
            Log.e(TAG, "DummyOnData callback should never be called!");
        }
    };

    @Override
    public void onDestroy() {
        Log.d(TAG, "Shareservice onDestroy");
        super.onDestroy();
    }
}
