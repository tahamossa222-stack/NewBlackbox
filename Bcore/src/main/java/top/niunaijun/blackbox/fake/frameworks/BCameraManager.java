package top.niunaijun.blackbox.fake.frameworks;

import android.os.RemoteException;

import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.core.system.ServiceManager;
import top.niunaijun.blackbox.core.system.camera.IBCameraManagerService;
import top.niunaijun.blackbox.entity.camera.BCameraConfig;
import top.niunaijun.blackbox.entity.camera.BFakeCamera;


public class BCameraManager extends BlackManager<IBCameraManagerService> {
    private static final BCameraManager sCameraManager = new BCameraManager();

    public static final int CLOSE_MODE = 0;
    public static final int GLOBAL_MODE = 1;
    public static final int OWN_MODE = 2;

    public static BCameraManager get() {
        return sCameraManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.CAMERA_MANAGER;
    }

    public static boolean isFakeCameraEnable() {
        return get().getPattern(BActivityThread.getUserId(), BActivityThread.getAppPackageName()) != CLOSE_MODE;
    }

    public static void disableFakeCamera(int userId, String pkg) {
        get().setPattern(userId, pkg, CLOSE_MODE);
    }

    public void setPattern(int userId, String pkg, int pattern) {
        try {
            getService().setPattern(userId, pkg, pattern);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public int getPattern(int userId, String pkg) {
        try {
            return getService().getPattern(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return CLOSE_MODE;
    }

    public void setFakeCamera(int userId, String pkg, BFakeCamera camera) {
        try {
            getService().setFakeCamera(userId, pkg, camera);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public BFakeCamera getFakeCamera(int userId, String pkg) {
        try {
            return getService().getFakeCamera(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setGlobalFakeCamera(BFakeCamera camera) {
        try {
            getService().setGlobalFakeCamera(camera);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public BFakeCamera getGlobalFakeCamera() {
        try {
            return getService().getGlobalFakeCamera();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public BCameraConfig getCameraConfig(int userId, String pkg) {
        try {
            return getService().getCameraConfig(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new BCameraConfig();
    }

    public void setCameraConfig(int userId, String pkg, BCameraConfig config) {
        try {
            getService().setCameraConfig(userId, pkg, config);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public BCameraConfig getGlobalCameraConfig() {
        try {
            return getService().getGlobalCameraConfig();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return new BCameraConfig();
    }

    public void setGlobalCameraConfig(BCameraConfig config) {
        try {
            getService().setGlobalCameraConfig(config);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public boolean isFakeCameraEnable(int userId, String pkg) {
        try {
            return getService().isFakeCameraEnable(userId, pkg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setFakeCameraEnable(int userId, String pkg, boolean enable) {
        try {
            getService().setFakeCameraEnable(userId, pkg, enable);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
