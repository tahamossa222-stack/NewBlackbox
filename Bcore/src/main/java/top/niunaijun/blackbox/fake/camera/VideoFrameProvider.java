package top.niunaijun.blackbox.fake.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decodes a video file with MediaCodec and presents the frames either
 * directly on a single hardware Surface (zero-copy) or, when the target
 * surface set changes / multiple surfaces are attached, on an internal
 * ImageReader followed by a CPU blit (YUV420 -> ARGB -> Canvas) to every
 * attached app surface.
 */
public class VideoFrameProvider {
    public static final String TAG = "VideoFrameProvider";

    private static final long FRAME_INTERVAL_MS = 33L;

    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;
    private ImageReader mImageReader;
    private ImageReader mDeviceImageReader;
    private HandlerThread mDecodeThread;
    private Handler mDecodeHandler;

    private final Object mLock = new Object();
    private final AtomicBoolean mIsRunning = new AtomicBoolean(false);
    private final AtomicBoolean mIsLooping = new AtomicBoolean(true);
    private final List<Surface> mTargets = new ArrayList<>();

    private String mVideoPath;
    private volatile int mWidth = 1280;
    private volatile int mHeight = 720;
    private volatile int mRotation = 0;

    private Bitmap mFrameBitmap;
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    public VideoFrameProvider() {
    }

    public void setResolution(int width, int height) {
        if (width > 0) mWidth = width;
        if (height > 0) mHeight = height;
    }

    public void setLooping(boolean looping) {
        mIsLooping.set(looping);
    }

    public boolean setVideoSource(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        File file = new File(path);
        if (!file.exists()) {
            Log.w(TAG, "Video file not found: " + path);
            return false;
        }
        mVideoPath = path;
        return true;
    }

    public boolean isRunning() {
        return mIsRunning.get();
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    /**
     * Throwaway surface handed to the REAL camera device in place of the
     * app's surfaces. Keeps the real stream "active" so capture results keep
     * flowing, while the app actually sees decoded video frames. Lives for
     * the whole session because the real device holds a reference to it.
     */
    public Surface getDeviceSurface() {
        synchronized (mLock) {
            if (mDeviceImageReader == null) {
                mDeviceImageReader = ImageReader.newInstance(mWidth, mHeight,
                        android.graphics.ImageFormat.YUV_420_888, 2);
                mDeviceImageReader.setOnImageAvailableListener(reader -> {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        image.close();
                    }
                }, null);
            }
            return mDeviceImageReader.getSurface();
        }
    }

    public void attachTarget(Surface surface) {
        if (surface == null || !surface.isValid()) {
            return;
        }
        synchronized (mLock) {
            if (mTargets.contains(surface)) {
                return;
            }
            mTargets.add(surface);
            if (mIsRunning.get()) {
                restart();
            }
        }
    }

    public void setTargets(List<Surface> surfaces) {
        List<Surface> next = new ArrayList<>();
        if (surfaces != null) {
            for (Surface s : surfaces) {
                if (s != null && s.isValid() && !next.contains(s)) {
                    next.add(s);
                }
            }
        }
        synchronized (mLock) {
            boolean changed = !mTargets.equals(next);
            mTargets.clear();
            mTargets.addAll(next);
            if (changed && mIsRunning.get()) {
                restart();
            }
        }
    }

    public void detachTarget(Surface surface) {
        synchronized (mLock) {
            if (mTargets.remove(surface) && mIsRunning.get()) {
                restart();
            }
        }
    }

    public boolean start() {
        if (mIsRunning.get()) {
            return true;
        }
        if (mVideoPath == null) {
            Log.e(TAG, "No video source set");
            return false;
        }
        synchronized (mLock) {
            if (mIsRunning.get()) {
                return true;
            }
            try {
                mDecodeThread = new HandlerThread("VideoFrameDecoder");
                mDecodeThread.start();
                mDecodeHandler = new Handler(mDecodeThread.getLooper());
                mIsRunning.set(true);
                mDecodeHandler.post(this::decodeLoop);
                Log.d(TAG, "Video provider started: " + mWidth + "x" + mHeight
                        + " targets=" + mTargets.size() + " path=" + mVideoPath);
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "Failed to start decoder", t);
                releaseInternal();
                return false;
            }
        }
    }

    public void stop() {
        synchronized (mLock) {
            mIsRunning.set(false);
            releaseInternal();
        }
    }

    private void restart() {
        synchronized (mLock) {
            mIsRunning.set(false);
            releasePipeline();
            if (mVideoPath != null) {
                mDecodeThread = new HandlerThread("VideoFrameDecoder");
                mDecodeThread.start();
                mDecodeHandler = new Handler(mDecodeThread.getLooper());
                mIsRunning.set(true);
                mDecodeHandler.post(this::decodeLoop);
            }
        }
    }

    private void releaseInternal() {
        releasePipeline();
        if (mDeviceImageReader != null) {
            try {
                mDeviceImageReader.close();
            } catch (Throwable ignored) {
            }
            mDeviceImageReader = null;
        }
    }

    /**
     * Tears down the decode pipeline but keeps the device-facing surface
     * alive: the real camera device still references it.
     */
    private void releasePipeline() {
        try {
            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
                mDecoder = null;
            }
            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
            if (mDecodeThread != null) {
                mDecodeThread.quitSafely();
                mDecodeThread = null;
                mDecodeHandler = null;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Release error", t);
        }
    }

    private void decodeLoop() {
        if (!mIsRunning.get()) {
            return;
        }
        // Guard against a stale runnable from a previous pipeline run: only
        // the current decode thread is allowed to drive the codec.
        HandlerThread current;
        synchronized (mLock) {
            current = mDecodeThread;
        }
        if (current == null || Thread.currentThread() != current) {
            return;
        }
        try {
            if (mDecoder == null) {
                if (!openDecoder()) {
                    mIsRunning.set(false);
                    return;
                }
            }

            // ---- feed one input sample ----
            int inputIndex = mDecoder.dequeueInputBuffer(10_000);
            if (inputIndex >= 0) {
                ByteBuffer inputBuffer = mDecoder.getInputBuffer(inputIndex);
                if (inputBuffer != null) {
                    int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
                    if (sampleSize < 0) {
                        if (mIsLooping.get()) {
                            mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                            sampleSize = mExtractor.readSampleData(inputBuffer, 0);
                            if (sampleSize < 0) {
                                mDecoder.queueInputBuffer(inputIndex, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                mIsRunning.set(false);
                                mDecodeHandler.postDelayed(this::decodeLoop,
                                        FRAME_INTERVAL_MS);
                                return;
                            }
                        } else {
                            mDecoder.queueInputBuffer(inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            mIsRunning.set(false);
                            return;
                        }
                    }
                    long sampleTime = mExtractor.getSampleTime();
                    mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0);
                    mExtractor.advance();

                    // ---- pacing: honor the video frame rate ----
                    long gap = sampleTime - mLastSampleTimeUs;
                    mLastSampleTimeUs = sampleTime;
                    if (gap > 0 && gap < 200_000) {
                        mDecodeHandler.postDelayed(this::decodeLoop, gap / 1000);
                        drainOutput();
                        return;
                    }
                }
            }

            drainOutput();
            mDecodeHandler.postDelayed(this::decodeLoop, FRAME_INTERVAL_MS);
        } catch (Throwable t) {
            Log.e(TAG, "Decode loop error", t);
            mDecodeHandler.postDelayed(this::decodeLoop, FRAME_INTERVAL_MS);
        }
    }

    private long mLastSampleTimeUs = -1;

    private void drainOutput() {
        if (mDecoder == null) {
            return;
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outputIndex = mDecoder.dequeueOutputBuffer(info, 0);
        if (outputIndex >= 0) {
            // Always render: in single-target mode it lands on the app's
            // Surface directly, otherwise on the internal ImageReader that
            // the blit path consumes.
            try {
                mDecoder.releaseOutputBuffer(outputIndex, true);
            } catch (Throwable t) {
                Log.w(TAG, "releaseOutputBuffer failed", t);
            }
        }
    }

    private boolean openDecoder() throws Exception {
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(mVideoPath);

        int videoTrack = -1;
        MediaFormat videoFormat = null;
        for (int i = 0; i < mExtractor.getTrackCount(); i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) {
                videoTrack = i;
                videoFormat = format;
                break;
            }
        }
        if (videoTrack < 0 || videoFormat == null) {
            Log.e(TAG, "No video track found");
            return false;
        }
        mExtractor.selectTrack(videoTrack);

        if (videoFormat.containsKey(MediaFormat.KEY_WIDTH)) {
            mWidth = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
        }
        if (videoFormat.containsKey(MediaFormat.KEY_HEIGHT)) {
            mHeight = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
        }
        if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
            mRotation = videoFormat.getInteger(MediaFormat.KEY_ROTATION);
        }
        Log.d(TAG, "Video track: " + mWidth + "x" + mHeight + " rotation=" + mRotation);

        synchronized (mLock) {
            Surface output;
            if (mTargets.size() == 1) {
                output = mTargets.get(0);
            } else {
                if (mImageReader == null) {
                    mImageReader = ImageReader.newInstance(mWidth, mHeight,
                            android.graphics.ImageFormat.YUV_420_888, 2);
                }
                mImageReader.setOnImageAvailableListener(this::onFrameAvailable,
                        mDecodeHandler);
                output = mImageReader.getSurface();
            }

            String mime = videoFormat.getString(MediaFormat.KEY_MIME);
            mDecoder = MediaCodec.createDecoderByType(mime);
            mDecoder.configure(videoFormat, output, null, 0);
            mDecoder.start();
            mLastSampleTimeUs = -1;
        }
        return true;
    }

    private void onFrameAvailable(ImageReader reader) {
        if (!mIsRunning.get()) {
            return;
        }
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }
            blitToTargets(image);
        } catch (Throwable t) {
            Log.w(TAG, "Frame blit error", t);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private void blitToTargets(Image image) {
        Bitmap frame = toArgbBitmap(image);
        if (frame == null) {
            return;
        }
        List<Surface> targets;
        synchronized (mLock) {
            targets = new ArrayList<>(mTargets);
        }
        for (Surface target : targets) {
            if (target == null || !target.isValid()) {
                continue;
            }
            Canvas canvas = null;
            try {
                canvas = target.lockHardwareCanvas();
            } catch (Throwable t) {
                try {
                    canvas = target.lockCanvas(null);
                } catch (Throwable ignored) {
                    canvas = null;
                }
            }
            if (canvas != null) {
                try {
                    drawFrame(canvas, frame);
                } finally {
                    try {
                        target.unlockCanvasAndPost(canvas);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private void drawFrame(Canvas canvas, Bitmap frame) {
        int vw = mRotation == 90 || mRotation == 270 ? mHeight : mWidth;
        int vh = mRotation == 90 || mRotation == 270 ? mWidth : mHeight;
        int cw = canvas.getWidth();
        int ch = canvas.getHeight();
        if (cw == 0 || ch == 0) {
            return;
        }
        float scale = Math.min((float) cw / vw, (float) ch / vh);
        int dw = (int) (vw * scale);
        int dh = (int) (vh * scale);
        Matrix matrix = new Matrix();
        matrix.postRotate(mRotation, vw / 2f, vh / 2f);
        matrix.postScale(scale, scale);
        matrix.postTranslate((cw - dw) / 2f, (ch - dh) / 2f);
        canvas.drawBitmap(frame, matrix, mPaint);
    }

    private Bitmap toArgbBitmap(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (mFrameBitmap == null || mFrameBitmap.getWidth() != width
                || mFrameBitmap.getHeight() != height) {
            mFrameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        try {
            Plane y = image.getPlanes()[0];
            Plane u = image.getPlanes()[1];
            Plane v = image.getPlanes()[2];
            ByteBuffer yBuffer = y.getBuffer();
            ByteBuffer uBuffer = u.getBuffer();
            ByteBuffer vBuffer = v.getBuffer();
            int yPixelStride = y.getPixelStride();
            int yRowStride = y.getRowStride();
            int uPixelStride = u.getPixelStride();
            int uRowStride = u.getRowStride();
            int vPixelStride = v.getPixelStride();
            int vRowStride = v.getRowStride();

            int halfW = (width + 1) / 2;
            int halfH = (height + 1) / 2;

            byte[] yBytes = new byte[yRowStride * height];
            byte[] uBytes = new byte[uRowStride * halfH];
            byte[] vBytes = new byte[vRowStride * halfH];
            yBuffer.get(yBytes, 0, yBytes.length);
            uBuffer.get(uBytes, 0, uBytes.length);
            vBuffer.get(vBytes, 0, vBytes.length);

            int[] pixels = new int[width * height];
            for (int row = 0; row < height; row++) {
                int yRowOffset = row * yRowStride;
                int uvRowOffset = (row >> 1) * uRowStride;
                int outRowOffset = row * width;
                for (int col = 0; col < width; col++) {
                    int yVal = yBytes[yRowOffset + col * yPixelStride] & 0xFF;
                    int uVal = uBytes[uvRowOffset + (col >> 1) * uPixelStride] & 0xFF;
                    int vVal = vBytes[uvRowOffset + (col >> 1) * vPixelStride] & 0xFF;

                    int c = yVal - 16;
                    int d = uVal - 128;
                    int e = vVal - 128;
                    int r = clamp(298 * c + 409 * e + 128);
                    int g = clamp(298 * c - 100 * d - 208 * e + 128);
                    int b = clamp(298 * c + 516 * d + 128);
                    pixels[outRowOffset + col] = Color.rgb(r, g, b);
                }
            }
            mFrameBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return mFrameBitmap;
        } catch (Throwable t) {
            Log.w(TAG, "YUV conversion error", t);
            return null;
        }
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }
}