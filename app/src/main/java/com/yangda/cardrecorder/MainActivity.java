package com.yangda.cardrecorder;

import static android.content.ContentValues.TAG;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private AlertDialog dialog;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        OpenCVLoader.initDebug();
        if (!checkOverlayDisplayPermission()) {
            requestOverlayDisplayPermission();
        }else {
            initMediaProjectionManager();
        }
        Button initButton = findViewById(R.id.init);
        initButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               initMediaProjectionManager();
            }
        });
    }

    private void requestOverlayDisplayPermission() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle("需要打开悬浮窗权限");
        builder.setMessage("在系统设置中为本应用打开悬浮窗权限,或允许该应用显示在其他应用上层。");
        builder.setPositiveButton("打开设置", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, RESULT_OK);
            }
        });
        dialog = builder.create();
        dialog.show();
    }

    /**
     * 检查是否已经打开悬浮窗权限
     * @return
     */
    private boolean checkOverlayDisplayPermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }
    MediaProjectionManager mediaProjectionManager;
    public static final int REQUEST_MEDIA_PROJECTION = 18;

    private void initMediaProjectionManager() {
        if (mediaProjectionManager != null) return;
        mediaProjectionManager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Intent intent = new Intent(MainActivity.this, FloatingWindowService.class);
            intent.putExtra("code", resultCode);
            intent.putExtra("data", data);
            startForegroundService(intent);
            finish();
        }
    }
}