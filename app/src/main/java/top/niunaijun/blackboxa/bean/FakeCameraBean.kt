package top.niunaijun.blackboxa.bean

import android.graphics.drawable.Drawable
import top.niunaijun.blackbox.entity.camera.BFakeCamera

data class FakeCameraBean(
    val userID: Int,
    val name: String,
    val icon: Drawable,
    val packageName: String,
    var fakeCameraPattern: Int,
    var fakeCamera: BFakeCamera?
) {
    val fakeCameraMode: String
        get() = when (fakeCamera?.mode) {
            BFakeCamera.LOCAL_VIDEO -> "Local Video"
            BFakeCamera.NETWORK_STREAM -> "Network Stream"
            BFakeCamera.LOCAL_IMAGE -> "Local Image"
            else -> "Real Camera"
        }
}
