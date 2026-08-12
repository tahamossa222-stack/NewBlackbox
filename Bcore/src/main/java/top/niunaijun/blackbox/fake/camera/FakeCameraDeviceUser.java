package top.niunaijun.blackbox.fake.camera;

import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.utils.Reflector;

/**
 * Wraps the real {@code android.hardware.camera2.impl.ICameraDeviceUser}
 * binder returned by the camera service:
 *
 *  - every configure call ({@code createStream}, {@code configureStreams},
 *    {@code endConfigure}) swaps the app's {@link Surface}s with the video
 *    provider's internal surface, so the REAL camera device streams into a
 *    black hole while capture results keep flowing to the app;
 *  - the app's original surfaces are recorded as render targets of the
 *    {@link VideoFrameProvider}, which pushes decoded video frames onto them;
 *  - {@code disconnect}/{@code close} tears the provider down.
 */
public class FakeCameraDeviceUser extends BinderInvocationStub {
    public static final String TAG = "FakeCameraDeviceUser";

    private final String mCameraId;
    private final VideoFrameProvider mVideoProvider;
    private final Object mWho;
    private final List<Surface> mConfiguredSurfaces = new ArrayList<>();

    public FakeCameraDeviceUser(IBinder realDeviceUser, String cameraId,
                                VideoFrameProvider provider) {
        super(realDeviceUser);
        mCameraId = cameraId;
        mVideoProvider = provider;
        mWho = asLocalInterface(realDeviceUser);
    }

    /**
     * android.hardware.camera2.impl.ICameraDeviceUser is a hidden interface;
     * resolve its Stub.asInterface() reflectively.
     */
    private static Object asLocalInterface(IBinder binder) {
        if (binder == null) {
            return null;
        }
        try {
            Class<?> stubClass = Class.forName(
                    "android.hardware.camera2.impl.ICameraDeviceUser$Stub");
            java.lang.reflect.Method asInterface = stubClass.getMethod(
                    "asInterface", IBinder.class);
            return asInterface.invoke(null, binder);
        } catch (Throwable t) {
            Log.e(TAG, "ICameraDeviceUser asInterface failed", t);
            return null;
        }
    }

    public String getCameraId() {
        return mCameraId;
    }

    public VideoFrameProvider getVideoProvider() {
        return mVideoProvider;
    }

    @Override
    protected Object getWho() {
        return mWho;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    protected void onBindMethod() {
        addMethodHook("createStream", new CreateStream(this));
        addMethodHook("configureStreams", new ConfigureStreams(this));
        addMethodHook("endConfigure", new EndConfigure(this));
        addMethodHook("disconnect", new Disconnect(this));
        addMethodHook("close", new Close(this));
    }

    private Surface readOutputConfigSurface(Object outputConfig) {
        try {
            Object surface = Reflector.on(outputConfig.getClass())
                    .field("mSurface").get(outputConfig);
            if (surface instanceof Surface) {
                return (Surface) surface;
            }
        } catch (Throwable t) {
            // older / vendor streams may expose it differently
        }
        try {
            Object surface = Reflector.on(outputConfig.getClass())
                    .method("getSurface").call(outputConfig);
            if (surface instanceof Surface) {
                return (Surface) surface;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void writeOutputConfigSurface(Object outputConfig, Surface replacement) {
        try {
            Reflector.on(outputConfig.getClass())
                    .field("mSurface").set(outputConfig, replacement);
        } catch (Throwable t) {
            Log.w(TAG, "Surface swap failed for OutputConfiguration", t);
        }
    }

    /**
     * Swaps the app surface inside a (possibly multi-stream)
     * StreamConfiguration parcelable and records the app surfaces.
     */
    private boolean swapSurfaces(Object streamConfiguration) {
        if (streamConfiguration == null) {
            return false;
        }
        boolean swapped = false;
        Surface internal = mVideoProvider.getDeviceSurface();

        // legacy configureStreams(): field "streams" -> List<OutputConfiguration>
        Object streams = null;
        try {
            streams = Reflector.on(streamConfiguration.getClass())
                    .field("streams").get(streamConfiguration);
        } catch (Throwable ignored) {
        }
        if (streams instanceof List) {
            for (Object outputConfig : (List<?>) streams) {
                Surface appSurface = readOutputConfigSurface(outputConfig);
                if (appSurface != null) {
                    synchronized (mConfiguredSurfaces) {
                        if (!mConfiguredSurfaces.contains(appSurface)) {
                            mConfiguredSurfaces.add(appSurface);
                        }
                    }
                }
                writeOutputConfigSurface(outputConfig, internal);
                swapped = true;
            }
            return swapped;
        }

        // modern createStream(): field "mOutputConfig" -> OutputConfiguration
        Object outputConfig = null;
        try {
            outputConfig = Reflector.on(streamConfiguration.getClass())
                    .field("mOutputConfig").get(streamConfiguration);
        } catch (Throwable ignored) {
        }
        if (outputConfig != null) {
            Surface appSurface = readOutputConfigSurface(outputConfig);
            if (appSurface != null) {
                synchronized (mConfiguredSurfaces) {
                    if (!mConfiguredSurfaces.contains(appSurface)) {
                        mConfiguredSurfaces.add(appSurface);
                    }
                }
            }
            writeOutputConfigSurface(outputConfig, internal);
            return true;
        }
        return false;
    }

    private void syncTargetsToProvider() {
        List<Surface> targets;
        synchronized (mConfiguredSurfaces) {
            targets = new ArrayList<>(mConfiguredSurfaces);
        }
        mVideoProvider.setTargets(targets);
    }

    private static class CreateStream extends MethodHook {
        private final FakeCameraDeviceUser mSelf;

        CreateStream(FakeCameraDeviceUser self) {
            mSelf = self;
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args.length > 0 && mSelf.swapSurfaces(args[0])) {
                Log.d(TAG, "createStream: surface swapped for camera " + mSelf.mCameraId);
            }
            mSelf.syncTargetsToProvider();
            return method.invoke(who, args);
        }
    }

    private static class ConfigureStreams extends MethodHook {
        private final FakeCameraDeviceUser mSelf;

        ConfigureStreams(FakeCameraDeviceUser self) {
            mSelf = self;
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args.length > 0 && mSelf.swapSurfaces(args[0])) {
                Log.d(TAG, "configureStreams: surfaces swapped for camera " + mSelf.mCameraId);
            }
            mSelf.syncTargetsToProvider();
            return method.invoke(who, args);
        }
    }

    private static class EndConfigure extends MethodHook {
        private final FakeCameraDeviceUser mSelf;

        EndConfigure(FakeCameraDeviceUser self) {
            mSelf = self;
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            // args: (int operationMode, CameraMetadataNative, Surface, ICameraDeviceCallbacks)
            if (args.length >= 3 && args[2] instanceof Surface && ((Surface) args[2]).isValid()) {
                args[2] = mSelf.mVideoProvider.getDeviceSurface();
            }
            mSelf.syncTargetsToProvider();
            Object result = method.invoke(who, args);
            mSelf.mVideoProvider.start();
            Log.d(TAG, "endConfigure done, video feed started for camera " + mSelf.mCameraId);
            return result;
        }
    }

    private static class Disconnect extends MethodHook {
        private final FakeCameraDeviceUser mSelf;

        Disconnect(FakeCameraDeviceUser self) {
            mSelf = self;
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            mSelf.mVideoProvider.stop();
            mSelf.mConfiguredSurfaces.clear();
            return method.invoke(who, args);
        }
    }

    private static class Close extends MethodHook {
        private final FakeCameraDeviceUser mSelf;

        Close(FakeCameraDeviceUser self) {
            mSelf = self;
        }

        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            mSelf.mVideoProvider.stop();
            mSelf.mConfiguredSurfaces.clear();
            return method.invoke(who, args);
        }
    }
}