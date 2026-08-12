package top.niunaijun.blackbox.fake.camera;

import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camera1 (android.hardware.Camera) interception bridge.
 *
 * Camera1's preview path runs in C++ (CameraJNI), so surface configuration
 * never reaches a Java binder proxy. The matching native hooks (see
 * cpp/Hook/CameraHook.cpp) are installed by {@link #ensureNativeHooks()} on
 * the JNI methods of {@code android.hardware.Camera} and call back into
 * {@link #notifySurface}/{@link #notifyStart}/{@link #notifyStop}/{@link
 * #notifyRelease} here. This class owns the video provider used by Camera1
 * apps and renders decoded frames into the SurfaceTexture/Surface the app
 * handed to the camera.
 */
public class Camera1Bridge {
    public static final String TAG = "Camera1Bridge";

    private static final AtomicBoolean sHooksInstalled = new AtomicBoolean(false);

    private static volatile VideoFrameProvider sProvider;
    private static volatile Surface sTargetSurface;

    private Camera1Bridge() {
    }

    /**
     * Installs hooks on all native (JNI) methods of android.hardware.Camera
     * whose JNI name matches the preview lifecycle. Method objects are
     * resolved by reflection so no API-level signature guessing is needed;
     * each matched method is forwarded to the native side, which patches the
     * ArtMethod via JniHook (Dobby).
     */
    public static void ensureNativeHooks() {
        if (!sHooksInstalled.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> cameraClass = Class.forName("android.hardware.Camera");
            for (Method method : cameraClass.getDeclaredMethods()) {
                if (!Modifier.isNative(method.getModifiers())) {
                    continue;
                }
                String name = method.getName();
                if (name.equals("native_setPreviewTexture")
                        || name.equals("native_setPreviewDisplay")
                        || name.equals("nativeSetPreviewTexture")
                        || name.equals("nativeSetPreviewDisplay")) {
                    installNativeHook(method, "SURFACE");
                } else if (name.equals("native_startPreview")
                        || name.equals("nativeStartPreview")) {
                    installNativeHook(method, "START");
                } else if (name.equals("native_stopPreview")
                        || name.equals("nativeStopPreview")) {
                    installNativeHook(method, "STOP");
                } else if (name.equals("native_release")
                        || name.equals("nativeRelease")) {
                    installNativeHook(method, "RELEASE");
                }
            }
            Log.d(TAG, "Camera1 JNI hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Camera1 hook installation failed", t);
        }
    }

    private static void installNativeHook(Method method, String command) {
        try {
            nativeInstallHook(method, command, method.getParameterCount());
        } catch (Throwable t) {
            Log.w(TAG, "installNativeHook failed: " + method.getName(), t);
        }
    }

    /**
     * Called from native when the app configures a preview surface.
     *
     * @param surfaceOrTexture the app's android.view.Surface or
     *                         android.graphics.SurfaceTexture
     */
    @SuppressWarnings("unused")
    public static void notifySurface(Object surfaceOrTexture) {
        if (surfaceOrTexture == null) {
            return;
        }
        try {
            Surface surface;
            if (surfaceOrTexture instanceof Surface) {
                surface = (Surface) surfaceOrTexture;
            } else if (surfaceOrTexture instanceof SurfaceTexture) {
                surface = new Surface((SurfaceTexture) surfaceOrTexture);
            } else {
                return;
            }
            sTargetSurface = surface;
            VideoFrameProvider provider = getProvider();
            if (provider != null) {
                provider.attachTarget(surface);
            }
        } catch (Throwable t) {
            Log.w(TAG, "notifySurface failed", t);
        }
    }

    /** Called from native when the app calls Camera.startPreview(). */
    @SuppressWarnings("unused")
    public static void notifyStart() {
        try {
            VideoFrameProvider provider = getProvider();
            if (provider != null) {
                provider.start();
            } else {
                Log.w(TAG, "notifyStart: no fake camera configured");
            }
        } catch (Throwable t) {
            Log.w(TAG, "notifyStart failed", t);
        }
    }

    /** Called from native when the app calls Camera.stopPreview(). */
    @SuppressWarnings("unused")
    public static void notifyStop() {
        try {
            VideoFrameProvider provider = getProvider();
            if (provider != null) {
                provider.stop();
            }
        } catch (Throwable t) {
            Log.w(TAG, "notifyStop failed", t);
        }
    }

    /** Called from native when the app calls Camera.release(). */
    @SuppressWarnings("unused")
    public static void notifyRelease() {
        try {
            VideoFrameProvider provider = sProvider;
            sProvider = null;
            sTargetSurface = null;
            if (provider != null) {
                provider.stop();
            }
        } catch (Throwable t) {
            Log.w(TAG, "notifyRelease failed", t);
        }
    }

    private static VideoFrameProvider getProvider() {
        VideoFrameProvider provider = sProvider;
        if (provider == null) {
            provider = top.niunaijun.blackbox.fake.service.CameraManagerHook.get()
                    .getCamera1Provider();
            sProvider = provider;
        }
        return provider;
    }

    private static native void nativeInstallHook(Method method, String command, int paramCount);
}