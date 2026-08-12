package top.niunaijun.blackbox.fake.service;

import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.camera.BFakeCamera;
import top.niunaijun.blackbox.fake.camera.Camera1Bridge;
import top.niunaijun.blackbox.fake.camera.FakeCameraDeviceUser;
import top.niunaijun.blackbox.fake.camera.VideoFrameProvider;
import top.niunaijun.blackbox.fake.frameworks.BCameraManager;

/**
 * Central registry linking the intercepted camera binders with their
 * {@link VideoFrameProvider}. The ICameraManager/ICameraService proxy calls
 * {@link #wrapDeviceUser(String, IBinder)} for Camera2 apps and
 * {@link #getCamera1Provider()} for Camera1 apps, so video frames are
 * rendered onto whatever surfaces the app configured.
 */
public class CameraManagerHook {
    public static final String TAG = "CameraManagerHook";
    private static final CameraManagerHook sInstance = new CameraManagerHook();

    private final Map<String, FakeCameraDeviceUser> mActiveDeviceUsers = new HashMap<>();
    private final Map<String, VideoFrameProvider> mActiveProviders = new HashMap<>();
    private VideoFrameProvider mCamera1Provider;

    static {
        // Install the native Camera1 (android.hardware.Camera) JNI hooks.
        Camera1Bridge.ensureNativeHooks();
    }

    public static CameraManagerHook get() {
        return sInstance;
    }

    public static boolean isFakeCameraEnabled() {
        return BCameraManager.isFakeCameraEnable();
    }

    public static BFakeCamera getCurrentFakeCamera() {
        if (!isFakeCameraEnabled()) {
            return null;
        }
        return BCameraManager.get().getFakeCamera(
                BActivityThread.getUserId(),
                BActivityThread.getAppPackageName());
    }

    private VideoFrameProvider createProvider(String tag) {
        BFakeCamera fakeCamera = getCurrentFakeCamera();
        if (fakeCamera == null || fakeCamera.isEmpty()) {
            Log.w(TAG, "No fake camera configured for current app (" + tag + ")");
            return null;
        }
        VideoFrameProvider provider = new VideoFrameProvider();
        if (!provider.setVideoSource(fakeCamera.getSourcePath())) {
            Log.e(TAG, "Invalid video source for " + tag + ": "
                    + fakeCamera.getSourcePath());
            return null;
        }
        provider.setResolution(fakeCamera.getWidth(), fakeCamera.getHeight());
        provider.setLooping(true);
        Log.d(TAG, "Video provider ready for " + tag + " source="
                + fakeCamera.getSourcePath());
        return provider;
    }

    /**
     * Camera2: wraps the real ICameraDeviceUser binder so that
     * configureStreams/createStream swap the app's surfaces with the video
     * engine while the app's own surfaces receive decoded frames.
     */
    public FakeCameraDeviceUser wrapDeviceUser(String cameraId, IBinder realDeviceUser) {
        if (realDeviceUser == null) {
            return null;
        }
        VideoFrameProvider provider = createProvider("camera2:" + cameraId);
        if (provider == null) {
            return null;
        }
        synchronized (this) {
            stopProviderLocked(cameraId);
            FakeCameraDeviceUser deviceUser = new FakeCameraDeviceUser(
                    realDeviceUser, cameraId, provider);
            deviceUser.injectHook();
            mActiveDeviceUsers.put(cameraId, deviceUser);
            mActiveProviders.put(cameraId, provider);
            return deviceUser;
        }
    }

    /**
     * Camera1: the shared provider used by {@link Camera1Bridge}; the native
     * JNI hooks feed it the app's SurfaceTexture as soon as it is set.
     */
    public VideoFrameProvider getCamera1Provider() {
        synchronized (this) {
            if (mCamera1Provider == null) {
                mCamera1Provider = createProvider("camera1");
            }
            return mCamera1Provider;
        }
    }

    public FakeCameraDeviceUser getDeviceUser(String cameraId) {
        synchronized (this) {
            return mActiveDeviceUsers.get(cameraId);
        }
    }

    public VideoFrameProvider getProvider(String cameraId) {
        synchronized (this) {
            return mActiveProviders.get(cameraId);
        }
    }

    public void stopProvider(String cameraId) {
        synchronized (this) {
            stopProviderLocked(cameraId);
        }
    }

    private void stopProviderLocked(String cameraId) {
        FakeCameraDeviceUser deviceUser = mActiveDeviceUsers.remove(cameraId);
        if (deviceUser != null) {
            deviceUser.getVideoProvider().stop();
        }
        VideoFrameProvider provider = mActiveProviders.remove(cameraId);
        if (provider != null) {
            provider.stop();
        }
    }

    public void stopAllProviders() {
        synchronized (this) {
            for (FakeCameraDeviceUser deviceUser : mActiveDeviceUsers.values()) {
                deviceUser.getVideoProvider().stop();
            }
            mActiveDeviceUsers.clear();
            for (VideoFrameProvider provider : mActiveProviders.values()) {
                provider.stop();
            }
            mActiveProviders.clear();
            if (mCamera1Provider != null) {
                mCamera1Provider.stop();
                mCamera1Provider = null;
            }
        }
    }
}