package top.niunaijun.blackbox.core.system.camera;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.AtomicFile;
import android.util.SparseArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;

import top.niunai.blackbox.core.env.BEnvironment;
import top.niunai.blackbox.core.system.ISystemService;
import top.niunai.blackbox.entity.camera.BCameraConfig;
import top.niunaijun.blackbox.entity.camera.BFakeCamera;
import top.niunaijun.blackbox.fake.frameworks.BCameraManager;
import top.niunaijun.blackbox.utils.CloseUtils;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.Slog;


public class BCameraManagerService extends IBCameraManagerService.Stub implements ISystemService {
    public static final String TAG = "BCameraManagerService";

    private static final BCameraManagerService sService = new BCameraManagerService();
    private final SparseArray<HashMap<String, BCameraConfig>> mCameraConfigs = new SparseArray<>();
    private final BCameraConfig mGlobalConfig = new BCameraConfig();

    public static BCameraManagerService get() {
        return sService;
    }

    private BCameraConfig getOrCreateConfig(int userId, String pkg) {
        synchronized (mCameraConfigs) {
            HashMap<String, BCameraConfig> pkgs = mCameraConfigs.get(userId);
            if (pkgs == null) {
                pkgs = new HashMap<>();
                mCameraConfigs.put(userId, pkgs);
            }
            BCameraConfig config = pkgs.get(pkg);
            if (config == null) {
                config = new BCameraConfig();
                config.pattern = BFakeCamera.DISABLED;
                pkgs.put(pkg, config);
            }
            return config;
        }
    }

    @Override
    public int getPattern(int userId, String pkg) {
        synchronized (mCameraConfigs) {
            BCameraConfig config = getOrCreateConfig(userId, pkg);
            return config.pattern;
        }
    }

    @Override
    public void setPattern(int userId, String pkg, int mode) {
        synchronized (mCameraConfigs) {
            getOrCreateConfig(userId, pkg).pattern = mode;
            save();
        }
    }

    @Override
    public BFakeCamera getFakeCamera(int userId, String pkg) {
        BCameraConfig config = getOrCreateConfig(userId, pkg);
        if (config.pattern == BCameraManager.OWN_MODE) {
            return config.fakeCamera;
        } else if (config.pattern == BCameraManager.GLOBAL_MODE) {
            return mGlobalConfig.fakeCamera;
        }
        return null;
    }

    @Override
    public void setFakeCamera(int userId, String pkg, BFakeCamera camera) {
        synchronized (mCameraConfigs) {
            getOrCreateConfig(userId, pkg).fakeCamera = camera;
            save();
        }
    }

    @Override
    public BFakeCamera getGlobalFakeCamera() {
        synchronized (mGlobalConfig) {
            return mGlobalConfig.fakeCamera;
        }
    }

    @Override
    public void setGlobalFakeCamera(BFakeCamera camera) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.fakeCamera = camera;
            save();
        }
    }

    @Override
    public BCameraConfig getCameraConfig(int userId, String pkg) {
        return getOrCreateConfig(userId, pkg);
    }

    @Override
    public void setCameraConfig(int userId, String pkg, BCameraConfig config) {
        synchronized (mCameraConfigs) {
            mCameraConfigs.get(userId).put(pkg, config);
            save();
        }
    }

    @Override
    public BCameraConfig getGlobalCameraConfig() {
        synchronized (mGlobalConfig) {
            return mGlobalConfig;
        }
    }

    @Override
    public void setGlobalCameraConfig(BCameraConfig config) {
        synchronized (mGlobalConfig) {
            mGlobalConfig.pattern = config.pattern;
            mGlobalConfig.fakeCamera = config.fakeCamera;
            mGlobalConfig.enabled = config.enabled;
            save();
        }
    }

    @Override
    public boolean isFakeCameraEnable(int userId, String pkg) {
        BCameraConfig config = getOrCreateConfig(userId, pkg);
        return config.enabled && config.pattern != BFakeCamera.DISABLED;
    }

    @Override
    public void setFakeCameraEnable(int userId, String pkg, boolean enable) {
        synchronized (mCameraConfigs) {
            getOrCreateConfig(userId, pkg).enabled = enable;
            save();
        }
    }

    public void save() {
        synchronized (mGlobalConfig) {
            synchronized (mCameraConfigs) {
                Parcel parcel = Parcel.obtain();
                AtomicFile atomicFile = new AtomicFile(BEnvironment.getFakeCameraConf());
                FileOutputStream fileOutputStream = null;
                try {
                    mGlobalConfig.writeToParcel(parcel, 0);

                    parcel.writeInt(mCameraConfigs.size());
                    for (int i = 0; i < mCameraConfigs.size(); i++) {
                        int tmpUserId = mCameraConfigs.keyAt(i);
                        HashMap<String, BCameraConfig> configArrayMap = mCameraConfigs.valueAt(i);
                        parcel.writeInt(tmpUserId);
                        parcel.writeMap(configArrayMap);
                    }
                    parcel.setDataPosition(0);
                    fileOutputStream = atomicFile.startWrite();
                    FileUtils.writeParcelToOutput(parcel, fileOutputStream);
                    atomicFile.finishWrite(fileOutputStream);
                } catch (Throwable e) {
                    e.printStackTrace();
                    atomicFile.failWrite(fileOutputStream);
                } finally {
                    parcel.recycle();
                    CloseUtils.close(fileOutputStream);
                }
            }
        }
    }

    public void loadConfig() {
        Parcel parcel = Parcel.obtain();
        InputStream is = null;
        try {
            File fakeCameraConf = BEnvironment.getFakeCameraConf();
            if (!fakeCameraConf.exists()) {
                return;
            }
            is = new FileInputStream(BEnvironment.getFakeCameraConf());
            byte[] bytes = FileUtils.toByteArray(is);
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);

            synchronized (mGlobalConfig) {
                mGlobalConfig.readFromParcel(parcel);
            }

            synchronized (mCameraConfigs) {
                mCameraConfigs.clear();
                int size = parcel.readInt();
                for (int i = 0; i < size; i++) {
                    int userId = parcel.readInt();
                    HashMap<String, BCameraConfig> configArrayMap = parcel.readHashMap(BCameraConfig.class.getClassLoader());
                    mCameraConfigs.put(userId, configArrayMap);
                    Slog.d(TAG, "load userId: " + userId + ", config: " + configArrayMap);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Slog.d(TAG, "bad config");
            FileUtils.deleteDir(BEnvironment.getFakeCameraConf());
        } finally {
            parcel.recycle();
            CloseUtils.close(is);
        }
    }

    @Override
    public void systemReady() {
        loadConfig();
    }
}
