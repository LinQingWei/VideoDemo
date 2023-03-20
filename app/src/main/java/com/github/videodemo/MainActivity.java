package com.github.videodemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private static final String FILE_NAME = "screen.h264";

    private H264DecodePlay mH264DecodePlay;
    private String mVideoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mVideoPath = getVideoFilePath();
        Log.d(TAG, "video path: " + mVideoPath);
        initView();
    }

    private String getVideoFilePath() {
        File dir = getFilesDir();
        Log.d(TAG, "dir: " + dir);
        File file = new File(dir, FILE_NAME);
        if (!file.exists()) {
            boolean res = false;
            try {
                res = file.createNewFile();
            } catch (IOException e) {
                Log.w(TAG, e);
            }
            if (res) {
                OutputStream os = null;
                InputStream is = null;
                try {
                    os = new FileOutputStream(file);
                    is = getAssets().open(FILE_NAME);
                    byte[] buffer = new byte[1024];
                    int len = 0;
                    while ((len = is.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                    os.flush();
                    is.close();
                    os.close();
                } catch (IOException e) {
                    Log.w(TAG, e);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        return file.getAbsolutePath();
    }

    private void initView() {
        SurfaceView surfaceView = findViewById(R.id.surface_view);
        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                mH264DecodePlay = new H264DecodePlay(mVideoPath, holder);
                mH264DecodePlay.decodePlay();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "surfaceChanged: " + format + ", " + width + ", " + height);
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                mH264DecodePlay.stop();
            }
        });
    }
}