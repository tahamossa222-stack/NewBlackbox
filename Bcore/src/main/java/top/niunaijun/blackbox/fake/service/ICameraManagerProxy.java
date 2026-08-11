package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.entity.camera.BFakeCamera;
import top.niunaijun.blackbox.fake.frameworks.BCameraManager;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.MethodParameterUtils;


public class ICameraManagerProxy extends BinderInvocationStub {
    public static final String TAG = "ICameraManagerProxy";

    public ICameraManagerProxy() {
        super(BRServiceManager.get().getService(Context.CAMERA_SERVICE));
    }

    @Override
    protected Object getWho() {
        return null;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodParameterUtils.replaceFirstAppPkg(args);
        return super.invoke(proxy, method, args);
    }

    @ProxyMethod("getCameraIdList")
    public static class GetCameraIdList extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BCameraManager.isFakeCameraEnable()) {
                String[] fakeIds = {"0", "1"};
                return fakeIds;
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                Log.w(TAG, "getCameraIdList failed, returning default");
                return new String[]{"0"};
            }
        }
    }

    @ProxyMethod("getCameraCharacteristics")
    public static class GetCameraCharacteristics extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BCameraManager.isFakeCameraEnable()) {
                Log.d(TAG, "Intercepted getCameraCharacteristics for fake camera");
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                Log.w(TAG, "getCameraCharacteristics failed");
                return null;
            }
        }
    }

    @ProxyMethod("openCameraDeviceUserAsync")
    public static class OpenCameraDeviceUserAsync extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BCameraManager.isFakeCameraEnable()) {
                Log.d(TAG, "Intercepted openCameraDeviceUserAsync for fake camera");
                String cameraId = args.length > 0 ? String.valueOf(args[0]) : "0";
                BFakeCamera fakeCamera = BCameraManager.get().getFakeCamera(
                        BActivityThread.getUserId(), 
                        BActivityThread.getAppPackageName());
                if (fakeCamera != null && !fakeCamera.isEmpty()) {
                    Log.d(TAG, "Using fake camera source: " + fakeCamera.getSourcePath());
                }
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                Log.w(TAG, "openCameraDeviceUserAsync failed");
                return null;
            }
        }
    }

    @ProxyMethod("connect")
    public static class Connect extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BCameraManager.isFakeCameraEnable()) {
                Log.d(TAG, "Intercepted camera connect for fake camera");
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                Log.w(TAG, "connect failed");
                return null;
            }
        }
    }

    @ProxyMethod("connectDevice")
    public static class ConnectDevice extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BCameraManager.isFakeCameraEnable()) {
                Log.d(TAG, "Intercepted connectDevice for fake camera");
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                Log.w(TAG, "connectDevice failed");
                return null;
            }
        }
    }

    @ProxyMethod("getNumberOfCameras")
    public static class GetNumberOfCameras extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BCameraManager.isFakeCameraEnable()) {
                return 2;
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                return 2;
            }
        }
    }

    @ProxyMethod("getCameraInfo")
    public static class GetCameraInfo extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (BCameraManager.isFakeCameraEnable()) {
                Log.d(TAG, "Intercepted getCameraInfo for fake camera");
            }
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                Log.w(TAG, "getCameraInfo failed");
                return null;
            }
        }
    }
}
