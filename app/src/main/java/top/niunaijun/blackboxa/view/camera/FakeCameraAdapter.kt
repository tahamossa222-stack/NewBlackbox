package top.niunaijun.blackboxa.view.camera

import android.view.View
import android.view.ViewGroup
import cbfg.rvadapter.RVHolder
import cbfg.rvadapter.RVHolderFactory
import top.niunaijun.blackbox.fake.frameworks.BCameraManager
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.FakeCameraBean
import top.niunaijun.blackboxa.databinding.ItemFakeBinding
import top.niunaijun.blackboxa.util.getString


class FakeCameraAdapter : RVHolderFactory() {

    override fun createViewHolder(parent: ViewGroup?, viewType: Int, item: Any): RVHolder<out Any> {
        return FakeCameraVH(inflate(R.layout.item_fake, parent))
    }

    class FakeCameraVH(itemView: View) : RVHolder<FakeCameraBean>(itemView) {

        private val binding = ItemFakeBinding.bind(itemView)

        override fun setContent(item: FakeCameraBean, isSelected: Boolean, payload: Any?) {
            binding.icon.setImageDrawable(item.icon)
            binding.name.text = item.name
            if (item.fakeCamera == null || item.fakeCameraPattern == BCameraManager.CLOSE_MODE) {
                binding.fakeLocation.text = getString(R.string.real_camera)
            } else {
                binding.fakeLocation.text = item.fakeCameraMode
            }
            binding.cornerLabel.visibility = View.VISIBLE
        }
    }
}
