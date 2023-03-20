package com.github.videodemo;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class H264DecodePlay {
    private static final String TAG = "H264Decode";

    private static final boolean UseSPSandPPS = false;

    private String mVideoPath;
    private MediaCodec mMediaCodec;
    private SurfaceHolder mSurfaceHolder;
    private boolean mStopFlag = false;

    H264DecodePlay(String videoPath, SurfaceHolder holder) {
        mVideoPath = videoPath;
        mSurfaceHolder = holder;
        initMediaCodec();
    }

    void decodePlay() {
        mMediaCodec.start();
        mMediaCodec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        new Thread(new Myrun()).start();
    }

    void stop() {
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
        }
    }

    private void initMediaCodec() {
        Log.i(TAG, "video path: " + mVideoPath);
        try {
            mMediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            final int width = mSurfaceHolder.getSurfaceFrame().width();
            final int height = mSurfaceHolder.getSurfaceFrame().height();
            Log.d(TAG, "width: " + width + ", height: " + height);
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);

            /*
            h264常见的帧头数据为：
                00 00 00 01 67    (SPS)
                00 00 00 01 68    (PPS)
                00 00 00 01 65    (IDR帧)
                00 00 00 01 61    (P帧)
            */

            //获取H264文件中的pps和sps数据
            if (UseSPSandPPS) {
                byte[] header_sps = {0, 0, 0, 1, 67, 66, 0, 42, (byte) 149, (byte) 168, 30, 0, (byte) 137, (byte) 249, 102, (byte) 224, 32, 32, 32, 64};
                byte[] header_pps = {0, 0, 0, 1, 68, (byte) 206, 60, (byte) 128, 0, 0, 0, 1, 6, (byte) 229, 1, (byte) 151, (byte) 128};
                mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
                mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
            }

            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 40);
            mMediaCodec.configure(mediaFormat, mSurfaceHolder.getSurface(), null, 0);
        } catch (IOException e) {
            Log.e(TAG, "failed to create mediacodec!", e);
        }
    }

    private byte[] getBytes(InputStream is) throws IOException {
        int len;
        int size = 1024;
        byte[] buf;
        if (is instanceof ByteArrayInputStream) {
            //返回可用的剩余字节
            size = is.available();
            //创建一个对应可用相应字节的字节数组
            buf = new byte[size];
            //读取这个文件并保存读取的长度
            len = is.read(buf, 0, size);
        } else {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            buf = new byte[size];
            while ((len = is.read(buf, 0, size)) != -1) {
                //将读取的数据写入到字节输出流
                bos.write(buf, 0, len);
            }
            //将这个流转换成字节数组
            buf = bos.toByteArray();
        }
        return buf;
    }

    private class Myrun implements Runnable {

        @Override
        public void run() {
            //获取一组输入缓存区
            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
            //解码后的数据，包含每一个buffer的元数据信息
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long startMs = System.currentTimeMillis();
            long timeoutUs = 10000;
            //用于检测文件头
            byte[] maker0 = new byte[]{0, 0, 0, 1};

            byte[] dummyFrame = new byte[]{0x00, 0x00, 0x01, 0x20};
            byte[] streamBuffer = null;

            File file = new File(mVideoPath);
            DataInputStream mInputStream = null;
            try {
                mInputStream = new DataInputStream(new FileInputStream(file));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            try {
                //返回可用的字节数组
                streamBuffer = getBytes(mInputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
            //得到可用字节数组长度
            int length = streamBuffer.length;
            Log.d(TAG, "length: " + length);

            if (length == 0) {
                streamBuffer = dummyFrame;
            }
            int startIndex = 0;
            //定义记录剩余字节的变量
            int remaining = length;
            File inFile = createNewFile(mVideoPath + "-in");
            File outFile = createNewFile(mVideoPath + "-out");
            while (true) {
                //当剩余的字节=0或者开始的读取的字节下标大于可用的字节数时  不在继续读取
                if (remaining == 0 || startIndex >= remaining) {
                    break;
                }
                //寻找帧头部
                int nextFrameStart = KMPMatch(maker0, streamBuffer, startIndex + 2, remaining);
                Log.d(TAG, "remaining: " + remaining + ", startIndex: " + startIndex + ", next: " + nextFrameStart);
                //找不到头部返回-1
                if (nextFrameStart == -1) {
                    nextFrameStart = remaining;
                }
                //得到可用的缓存区
                int inputIndex = mMediaCodec.dequeueInputBuffer(timeoutUs);
                Log.d(TAG, "inputIndex: " + inputIndex);
                //有可用缓存区
                if (inputIndex >= 0) {
                    ByteBuffer byteBuffer = inputBuffers[inputIndex];
                    byteBuffer.clear();
                    //将可用的字节数组，传入缓冲区
                    byteBuffer.put(streamBuffer, startIndex, nextFrameStart - startIndex);
                    writeByteBufferToFile(byteBuffer, inFile);
                    //把数据传递给解码器
                    mMediaCodec.queueInputBuffer(inputIndex, 0, nextFrameStart - startIndex, 0, 0);
                    //指定下一帧的位置
                    startIndex = nextFrameStart;
                } else {
                    continue;
                }

                int outputIndex = mMediaCodec.dequeueOutputBuffer(info, timeoutUs);
                Log.d(TAG, "outputIndex: " + outputIndex);
                if (outputIndex >= 0) {
                    //帧控制是不在这种情况下工作，因为没有PTS H264是可用的
                    while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    boolean doRender = (info.size != 0);
                    ByteBuffer byteBuffer = mMediaCodec.getOutputBuffer(outputIndex);
                    writeByteBufferToFile(byteBuffer, outFile);
                    //对outputbuffer的处理完后，调用这个函数把buffer重新返回给codec类。
                    mMediaCodec.releaseOutputBuffer(outputIndex, doRender);
                }
            }
        }
    }

    /**
     * 查找帧头部的位置
     *
     * @param pattern 文件头字节数组
     * @param bytes   可用的字节数组
     * @param start   开始读取的下标
     * @param remain  可用的字节数量
     * @return
     */
    private int KMPMatch(byte[] pattern, byte[] bytes, int start, int remain) {
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int[] lsp = computeLspTable(pattern);

        int j = 0;  // Number of chars matched in pattern
        for (int i = start; i < remain; i++) {
            while (j > 0 && bytes[i] != pattern[j]) {
                // Fall back in the pattern
                j = lsp[j - 1];  // Strictly decreasing
            }
            if (bytes[i] == pattern[j]) {
                // Next char matched, increment position
                j++;
                if (j == pattern.length) return i - (j - 1);
            }
        }

        return -1;  // Not found
    }

    // 0 1 2 0
    private int[] computeLspTable(byte[] pattern) {
        int[] lsp = new int[pattern.length];
        lsp[0] = 0;  // Base case
        for (int i = 1; i < pattern.length; i++) {
            // Start by assuming we're extending the previous LSP
            int j = lsp[i - 1];
            while (j > 0 && pattern[i] != pattern[j]) j = lsp[j - 1];
            if (pattern[i] == pattern[j]) j++;
            lsp[i] = j;
        }
        return lsp;
    }

    private void writeByteBufferToFile(@NonNull ByteBuffer byteBuffer, @NonNull File file) {
        FileChannel fileChannel = null;
        try {
            fileChannel = new FileOutputStream(file, true).getChannel();
            fileChannel.write(byteBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileChannel != null) {
                try {
                    fileChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private File createNewFile(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            file.delete();
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return file;
    }
}
