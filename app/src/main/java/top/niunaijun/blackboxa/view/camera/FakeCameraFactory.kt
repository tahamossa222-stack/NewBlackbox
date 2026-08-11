package top.niunaijun.blackboxa.view.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import top.niunaijun.blackboxa.data.FakeCameraRepository


class FakeCameraFactory(private val repo: FakeCameraRepository) :
    ViewModelProvider.NewInstanceFactory() {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FakeCameraViewModel(repo) as T
    }
}
