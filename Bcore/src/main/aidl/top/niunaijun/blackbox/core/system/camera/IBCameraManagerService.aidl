// IBCameraManagerService.aidl
package top.niunaijun.blackbox.core.system.camera;

import top.niunaijun.blackbox.entity.camera.BFakeCamera;
import top.niunaijun.blackbox.entity.camera.BCameraConfig;

interface IBCameraManagerService {
    int getPattern(int userId, String pkg);
    void setPattern(int userId, String pkg, int mode);
    
    BFakeCamera getFakeCamera(int userId, String pkg);
    void setFakeCamera(int userId, String pkg, in BFakeCamera camera);
    
    BFakeCamera getGlobalFakeCamera();
    void setGlobalFakeCamera(in BFakeCamera camera);
    
    BCameraConfig getCameraConfig(int userId, String pkg);
    void setCameraConfig(int userId, String pkg, in BCameraConfig config);
    
    BCameraConfig getGlobalCameraConfig();
    void setGlobalCameraConfig(in BCameraConfig config);
    
    boolean isFakeCameraEnable(int userId, String pkg);
    void setFakeCameraEnable(int userId, String pkg, boolean enable);
}
