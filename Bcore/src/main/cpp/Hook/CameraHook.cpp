#include <jni.h>
#include <string.h>
#include "CameraHook.h"
#include "BaseHook.h"
#import "JniHook/JniHook.h"

#define BRIDGE_CLASS "top/niunaijun/blackbox/fake/camera/Camera1Bridge"

static JavaVM *gVm = nullptr;

static jclass gBridgeClass = nullptr;
static jmethodID gNotifySurface = nullptr;
static jmethodID gNotifyStart = nullptr;
static jmethodID gNotifyStop = nullptr;
static jmethodID gNotifyRelease = nullptr;

JNIEnv *cameraGetEnv() {
    JNIEnv *env = nullptr;
    if (gVm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        gVm->AttachCurrentThread(&env, nullptr);
    }
    return env;
}

static void ensureBridgeRefs(JNIEnv *env) {
    if (gBridgeClass != nullptr) {
        return;
    }
    jclass clazz = env->FindClass(BRIDGE_CLASS);
    if (clazz == nullptr) {
        env->ExceptionClear();
        return;
    }
    gBridgeClass = (jclass) env->NewGlobalRef(clazz);
    gNotifySurface = env->GetStaticMethodID(gBridgeClass, "notifySurface",
                                            "(Ljava/lang/Object;)V");
    gNotifyStart = env->GetStaticMethodID(gBridgeClass, "notifyStart", "()V");
    gNotifyStop = env->GetStaticMethodID(gBridgeClass, "notifyStop", "()V");
    gNotifyRelease = env->GetStaticMethodID(gBridgeClass, "notifyRelease", "()V");
}

// JNI: native_setPreviewTexture(SurfaceTexture) / native_setPreviewDisplay(Surface)
static void (*origSetSurface)(JNIEnv *env, jobject thiz, jobject surface) = nullptr;
static void newSetSurface(JNIEnv *env, jobject thiz, jobject surface) {
    if (gBridgeClass != nullptr && gNotifySurface != nullptr) {
        env->CallStaticVoidMethod(gBridgeClass, gNotifySurface, surface);
    }
}

// JNI: native_setPreviewDisplay(SurfaceHolder, Surface) on newer APIs
static void (*origSetDisplay)(JNIEnv *env, jobject thiz, jobject holder,
                              jobject surface) = nullptr;
static void newSetDisplay(JNIEnv *env, jobject thiz, jobject holder, jobject surface) {
    if (gBridgeClass != nullptr && gNotifySurface != nullptr) {
        env->CallStaticVoidMethod(gBridgeClass, gNotifySurface, surface);
    }
}

static void (*origStartPreview)(JNIEnv *env, jobject thiz) = nullptr;
static void newStartPreview(JNIEnv *env, jobject thiz) {
    if (gBridgeClass != nullptr && gNotifyStart != nullptr) {
        env->CallStaticVoidMethod(gBridgeClass, gNotifyStart);
    }
}

static void (*origStopPreview)(JNIEnv *env, jobject thiz) = nullptr;
static void newStopPreview(JNIEnv *env, jobject thiz) {
    if (gBridgeClass != nullptr && gNotifyStop != nullptr) {
        env->CallStaticVoidMethod(gBridgeClass, gNotifyStop);
    }
}

static void (*origRelease)(JNIEnv *env, jobject thiz) = nullptr;
static void newRelease(JNIEnv *env, jobject thiz) {
    if (gBridgeClass != nullptr && gNotifyRelease != nullptr) {
        env->CallStaticVoidMethod(gBridgeClass, gNotifyRelease);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_top_niunaijun_blackbox_fake_camera_Camera1Bridge_nativeInstallHook(
        JNIEnv *env, jclass clazz, jobject method, jstring command, jint param_count) {
    ensureBridgeRefs(env);
    const char *cmd = env->GetStringUTFChars(command, nullptr);
    void *new_fun = nullptr;
    void **orig_fun = nullptr;

    if (strcmp(cmd, "SURFACE") == 0) {
        if (param_count == 2) {
            new_fun = (void *) newSetDisplay;
            orig_fun = (void **) (&origSetDisplay);
        } else if (param_count == 1) {
            new_fun = (void *) newSetSurface;
            orig_fun = (void **) (&origSetSurface);
        }
    } else if (strcmp(cmd, "START") == 0) {
        new_fun = (void *) newStartPreview;
        orig_fun = (void **) (&origStartPreview);
    } else if (strcmp(cmd, "STOP") == 0) {
        new_fun = (void *) newStopPreview;
        orig_fun = (void **) (&origStopPreview);
    } else if (strcmp(cmd, "RELEASE") == 0) {
        new_fun = (void *) newRelease;
        orig_fun = (void **) (&origRelease);
    }

    if (new_fun != nullptr && orig_fun != nullptr) {
        try {
            JniHook::HookJniFun(env, method, new_fun, orig_fun, false);
            ALOGD("CameraHook: hooked %s", cmd);
        } catch (...) {
            ALOGD("CameraHook: failed to hook %s", cmd);
        }
    }
    env->ReleaseStringUTFChars(command, cmd);
}

void CameraHook::init(JNIEnv *env) {
    env->GetJavaVM(&gVm);
    // Hook installation itself happens from the bridge the first time it
    // is invoked; nothing else to do here.
}