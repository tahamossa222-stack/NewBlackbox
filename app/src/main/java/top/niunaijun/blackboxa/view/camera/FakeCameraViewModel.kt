package top.niunaijun.blackboxa.view.camera

import androidx.lifecycle.MutableLiveData
import top.niunaijun.blackbox.entity.camera.BFakeCamera
import top.niunaijun.blackboxa.bean.FakeCameraBean
import top.niunaijun.blackboxa.data.FakeCameraRepository
import top.niunaijun.blackboxa.view.base.BaseViewModel


class FakeCameraViewModel(private val mRepo: FakeCameraRepository) : BaseViewModel() {

    val appsLiveData = MutableLiveData<List<FakeCameraBean>>()


    fun getInstallAppList(userID: Int) {
        launchOnUI {
            mRepo.getInstalledAppList(userID, appsLiveData)
        }
    }

    fun setPattern(userId: Int, pkg: String, pattern: Int) {
        launchOnUI {
            mRepo.setPattern(userId, pkg, pattern)
        }
    }

    fun setFakeCamera(userId: Int, pkg: String, fakeCamera: BFakeCamera) {
        launchOnUI {
            mRepo.setFakeCamera(userId, pkg, fakeCamera)
        }
    }

}
