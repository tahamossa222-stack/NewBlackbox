package top.niunaijun.blackboxa.util

import top.niunaijun.blackboxa.data.AppsRepository
import top.niunaijun.blackboxa.data.FakeCameraRepository
import top.niunaijun.blackboxa.data.FakeLocationRepository
import top.niunaijun.blackboxa.data.GmsRepository

import top.niunaijun.blackboxa.view.apps.AppsFactory
import top.niunaijun.blackboxa.view.camera.FakeCameraFactory
import top.niunaijun.blackboxa.view.fake.FakeLocationFactory
import top.niunaijun.blackboxa.view.gms.GmsFactory
import top.niunaijun.blackboxa.view.list.ListFactory



object InjectionUtil {

    private val appsRepository = AppsRepository()



    private val gmsRepository = GmsRepository()

    private val fakeLocationRepository = FakeLocationRepository()

    private val fakeCameraRepository = FakeCameraRepository()

    fun getAppsFactory() : AppsFactory {
        return AppsFactory(appsRepository)
    }

    fun getListFactory(): ListFactory {
        return ListFactory(appsRepository)
    }


    fun getGmsFactory():GmsFactory{
        return GmsFactory(gmsRepository)
    }

    fun getFakeLocationFactory():FakeLocationFactory{
        return FakeLocationFactory(fakeLocationRepository)
    }

    fun getFakeCameraFactory():FakeCameraFactory{
        return FakeCameraFactory(fakeCameraRepository)
    }
}