package com.yangda.cardrecorder;

import static android.content.ContentValues.TAG;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Objects;

public class FloatingWindowService extends Service {
    private WindowManager windowManager;
    private ViewGroup floatView;
    private int LAYOUT_TYPE;
    private WindowManager.LayoutParams floatWindowLayoutParam;
    private Context context;
    private int[] cardCounts, player1Card, player2Card;
    private int[] handCard = {245 , 900, 2800, 1050};
    private int[] player1 = {1890, 321, 2664, 554};
    private int[] player2 = {460, 321, 1300, 554};
    private float ratio;//适配不同屏幕大小，需要缩放或扩大的比例

    private int time;
    @Override
    public void onCreate() {
        super.onCreate();
        this.context = this.getApplicationContext();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = ImageUtils.getHasVirtualKey(windowManager);
        ratio = mScreenHeight / 3200f;
        for (int i = 0; i < handCard.length; i++) {
            handCard[i] = (int)(handCard[i] * ratio);
            player1[i] = (int)(player1[i] * ratio);
            player2[i] = (int)(player2[i] * ratio);
        }
        LayoutInflater inflater = (LayoutInflater) getBaseContext().getSystemService(LAYOUT_INFLATER_SERVICE);
        floatView = (ViewGroup) inflater.inflate(R.layout.floating_layout, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_TYPE = WindowManager.LayoutParams.TYPE_TOAST;
        }
        floatWindowLayoutParam = new WindowManager.LayoutParams(mScreenWidth, mScreenHeight / 10, LAYOUT_TYPE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        floatWindowLayoutParam.gravity = Gravity.CENTER;
        floatWindowLayoutParam.x = 0;
        floatWindowLayoutParam.y = 0;
        SharedPreferences sharedPreferences = getSharedPreferences("MySettings", Context.MODE_PRIVATE);
        time = sharedPreferences.getInt("time", 2000);
        windowManager.addView(floatView, floatWindowLayoutParam);
        //触摸移动悬浮框
        floatView.setOnTouchListener(new View.OnTouchListener() {
            final WindowManager.LayoutParams floatWindowLayoutUpdateParam = floatWindowLayoutParam;
            double x,y,px,py;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        x = floatWindowLayoutUpdateParam.x;
                        y = floatWindowLayoutUpdateParam.y;
                        px = event.getRawX();
                        py = event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        floatWindowLayoutUpdateParam.x = (int) ((x + event.getRawX()) - px);
                        floatWindowLayoutUpdateParam.y = (int) ((y + event.getRawY()) - py);
                        windowManager.updateViewLayout(floatView, floatWindowLayoutUpdateParam);
                        break;
                }
                return false;
            }
        });

        Button startButton = floatView.findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createImageReader();
                virtualDisplay();
                initScreenShot();
            }
        });
        Button stopButton = floatView.findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScreenShot();
            }
        });
    }
    MediaProjectionManager mediaProjectionManager;
    MediaProjection mMediaProjection;

    /**
     * 由于创建MediaProjection必须要code和Data数据而这数据只能在activity里获取，所以使用Intent传递来自Activity的数据
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        createNotificationChannel();
        int mResultCode = intent.getIntExtra("code", -1);
        Intent mResultData = intent.getParcelableExtra("data");
        mediaProjectionManager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mMediaProjection = mediaProjectionManager.getMediaProjection(mResultCode, Objects.requireNonNull(mResultData));
        Log.e(TAG, "mMediaProjection created: " + mMediaProjection);
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 当切换横屏竖屏时需要转换的数据
     * @param newConfig
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int big = Math.max(mScreenHeight, mScreenWidth), small = Math.min(mScreenHeight, mScreenWidth);
        // 检查是否是横屏模式
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mScreenWidth = big;
            mScreenHeight = small;
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            mScreenWidth = small;
            mScreenHeight = big;
        }
        // 更新窗口布局
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager != null) {
            floatWindowLayoutParam.width = mScreenWidth;
            floatWindowLayoutParam.height = 200;
            windowManager.updateViewLayout(floatView, floatWindowLayoutParam);
        }
    }

    private void createNotificationChannel() {
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器
        Intent nfIntent = new Intent(this, MainActivity.class); //点击后跳转的界面，可以设置跳转数据
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, PendingIntent.FLAG_IMMUTABLE)) // 设置PendingIntent
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                .setContentText("is running......") // 设置上下文内容
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间
        /*以下是对Android 8.0的适配*/
        //普通notification适配
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("notification_id");
        }
        //前台服务notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
        Notification notification = builder.build(); // 获取构建好的Notification
        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
        startForeground(110, notification);
    }
    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;
    private ImageReader mImageReader;
    private void createImageReader() {
        if (mImageReader != null)return;
        // 设置截屏的宽高
        mImageReader = ImageReader.newInstance(mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 1);
    }

    private VirtualDisplay mVirtualDisplay;
    /**
     * 最终得到当前屏幕的内容，注意这里mImageReader.getSurface()被传入，屏幕的数据也将会在ImageReader中的Surface中
     */
    private void virtualDisplay() {
        if (mVirtualDisplay != null)return;
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("screen-mirror",
                mScreenWidth, mScreenHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
    }

    private boolean isScreenshotEnabled = true;

    public boolean isChange(int[] last,int[] current){
        for (int i = 0; i < last.length; i++) {
            if (current[i] != 0){
                break;
            }
        }
        for (int i = 0; i < last.length; i++) {
            if (current[i] != last[i]){
                return true;
            }
        }
        return false;
    }
    private Handler handler = new Handler();
    public void initScreenShot(){
        handler.removeCallbacksAndMessages(null);
        isScreenshotEnabled = true;
        TextView textView1 = floatView.findViewById(getResources().getIdentifier("stopButton", "id", getPackageName()));
        textView1.setText("停止");
        this.cardCounts = new int[15];
        this.player1Card = new int[15];
        this.player2Card = new int[15];
        Arrays.fill(cardCounts,4);
        cardCounts[14] = 2;
        updatePlayerCard();
        handler.postDelayed(new Runnable() {
            public void run() {
                Image image = mImageReader.acquireLatestImage();
                Bitmap bitmap = ImageUtils.imageToBitmap(image);
                bitmap = ImageUtils.cropBitmap(bitmap, handCard);
                int[] matches = CardDetectionUtil.findMatches(context, bitmap, 0.85, true, ratio);
                updateContent(matches);
                startScreenShot();
            }
        }, 1000);
    }

    private void startScreenShot() {
        if (isScreenshotEnabled) {
            handler.postDelayed(new Runnable() {
                public void run() {
                    Image image = mImageReader.acquireLatestImage();
                    Bitmap bitmap = ImageUtils.imageToBitmap(image);
                    Bitmap bitmap1 = ImageUtils.cropBitmap(bitmap, player1);
                    Bitmap bitmap2 = ImageUtils.cropBitmap(bitmap, player2);
                    int[] matches1 = CardDetectionUtil.findMatches(context, bitmap1, 0.85, false, ratio);
                    int[] matches2 = CardDetectionUtil.findMatches(context, bitmap2, 0.85, false, ratio);
                    if (isChange(player1Card, matches1))updateContent(matches1);
                    if (isChange(player2Card, matches2))updateContent(matches2);
                    player1Card = matches1;
                    player2Card = matches2;
                    updatePlayerCard();
                    startScreenShot();
                }
            }, time);
        }
    }
    String[] name = {" ","A","2","3","4","5","6","7","8","9","10","J","Q","K","王"};

    /**
     * 更新界面上记牌器的显示，传入的参数为此次更新需要减点的点数
     * @param value
     */
    public void updateContent(int[] value){
        for (int i = 1; i < 15; i++) {
            cardCounts[i] -= value[i];
            TextView textView = floatView.findViewById(getResources().getIdentifier("textView" + i, "id", getPackageName()));
            textView.setText(name[i] + "\n" +cardCounts[i]);
        }
    }

    /**
     * 更新玩家当前的出牌数，以及当前时间
     */
    public void updatePlayerCard(){
        StringBuilder s1 = new StringBuilder();
        StringBuilder s2 = new StringBuilder();
        for (int i = 1; i < 15; i++) {
            if (player1Card[i] != 0){
                for (int j = 0; j < player1Card[i]; j++) {
                    s1.append(name[i]);
                }
            }
            if (player2Card[i] != 0){
                for (int j = 0; j < player2Card[i]; j++) {
                    s2.append(name[i]);
                }
            }
        }
        if (s1.length()==0)s1.append("空");
        if (s2.length()==0)s2.append("空");
        TextView textView1 = floatView.findViewById(getResources().getIdentifier("player1", "id", getPackageName()));
        textView1.setText(s1.toString());
        TextView textView2 = floatView.findViewById(getResources().getIdentifier("player2", "id", getPackageName()));
        textView2.setText(s2.toString());
        TextView textView = floatView.findViewById(getResources().getIdentifier("textView", "id", getPackageName()));
        textView.setText("时间" + (System.currentTimeMillis()/1000) % 100);
    }
    // 在需要停止截图的地方调用此方法
    private void stopScreenShot() {
        if(isScreenshotEnabled){
            isScreenshotEnabled = false;
            handler.removeCallbacksAndMessages(null);
            TextView textView1 = floatView.findViewById(getResources().getIdentifier("stopButton", "id", getPackageName()));
            textView1.setText("继续");
        }else {
            isScreenshotEnabled = true;
            TextView textView1 = floatView.findViewById(getResources().getIdentifier("stopButton", "id", getPackageName()));
            textView1.setText("停止");
            startScreenShot();
        }
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
