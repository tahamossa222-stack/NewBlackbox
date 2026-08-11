package top.niunaijun.blackboxa.data

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.lifecycle.MutableLiveData
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.entity.camera.BFakeCamera
import top.niunaijun.blackbox.fake.frameworks.BCameraManager
import top.niunaijun.blackboxa.bean.FakeCameraBean


class FakeCameraRepository {
    val TAG: String = "FakeCameraRepository"

    fun setPattern(userId: Int, pkg: String, pattern: Int) {
        BCameraManager.get().setPattern(userId, pkg, pattern)
    }

    private fun getPattern(userId: Int, pkg: String): Int {
        return BCameraManager.get().getPattern(userId, pkg)
    }

    private fun getFakeCamera(userId: Int, pkg: String): BFakeCamera? {
        return BCameraManager.get().getFakeCamera(userId, pkg)
    }

    fun setFakeCamera(userId: Int, pkg: String, camera: BFakeCamera) {
        BCameraManager.get().setFakeCamera(userId, pkg, camera)
    }

    fun getInstalledAppList(
        userID: Int,
        appsFakeLiveData: MutableLiveData<List<FakeCameraBean>>
    ) {
        val installedList = mutableListOf<FakeCameraBean>()
        val installedApplications: List<ApplicationInfo> =
            BlackBoxCore.get().getInstalledApplications(0, userID)
        
        for (installedApplication in installedApplications) {
            val info = FakeCameraBean(
                userID,
                installedApplication.loadLabel(BlackBoxCore.getPackageManager()).toString(),
                installedApplication.loadIcon(BlackBoxCore.getPackageManager()),
                installedApplication.packageName,
                getPattern(userID, installedApplication.packageName),
                getFakeCamera(userID, installedApplication.packageName)
            )
            installedList.add(info)
        }

        Log.d(TAG, installedList.joinToString(","))
        appsFakeLiveData.postValue(installedList)
    }
}
