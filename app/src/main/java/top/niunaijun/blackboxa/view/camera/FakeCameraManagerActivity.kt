package top.niunaijun.blackboxa.view.camera

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import cbfg.rvadapter.RVAdapter
import com.afollestad.materialdialogs.MaterialDialog
import com.ferfalk.simplesearchview.SimpleSearchView
import top.niunaijun.blackbox.fake.frameworks.BCameraManager
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.bean.FakeCameraBean
import top.niunaijun.blackboxa.databinding.ActivityListBinding
import top.niunaijun.blackboxa.util.InjectionUtil
import top.niunaijun.blackboxa.util.inflate
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.base.BaseActivity


class FakeCameraManagerActivity : BaseActivity() {
    val TAG: String = "FakeCameraManagerActivity"

    private val viewBinding: ActivityListBinding by inflate()

    private lateinit var mAdapter: RVAdapter<FakeCameraBean>

    private lateinit var viewModel: FakeCameraViewModel

    private var appList: List<FakeCameraBean> = ArrayList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        initToolbar(viewBinding.toolbarLayout.toolbar, R.string.fake_camera, true)

        mAdapter = RVAdapter<FakeCameraBean>(this, FakeCameraAdapter()).bind(viewBinding.recyclerView)
            .setItemClickListener { _, data, _ ->
                val intent = Intent(this, CameraSettingActivity::class.java)
                intent.putExtra("user_id", data.userID)
                intent.putExtra("package_name", data.packageName)
                intent.putExtra("app_name", data.name)
                startActivity(intent)
            }.setItemLongClickListener { _, item, position ->
                disableFakeCamera(item, position)
            }

        viewBinding.recyclerView.layoutManager = LinearLayoutManager(this)


        initSearchView()
        initViewModel()
    }

    private fun disableFakeCamera(item: FakeCameraBean, position: Int) {
        MaterialDialog(this).show {
            title(R.string.close_fake_camera)
            message(text = getString(R.string.close_app_fake_camera, item.name))
            negativeButton(R.string.cancel)
            positiveButton(R.string.done) {
                BCameraManager.disableFakeCamera(currentUserID(), item.packageName)
                toast(getString(R.string.close_fake_camera_success, item.name))
                item.fakeCameraPattern = BCameraManager.CLOSE_MODE
                mAdapter.replaceAt(position, item)
            }
        }
    }

    private fun initSearchView() {
        viewBinding.searchView.setOnQueryTextListener(object :
            SimpleSearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String): Boolean {
                filterApp(newText)
                return true
            }

            override fun onQueryTextCleared(): Boolean {
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                return true
            }

        })
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this, InjectionUtil.getFakeCameraFactory()).get(
            FakeCameraViewModel::class.java
        )
        loadAppList()
        viewBinding.toolbarLayout.toolbar.setTitle(R.string.fake_camera)

        viewModel.appsLiveData.observe(this) {
            if (it != null) {
                this.appList = it
                viewBinding.searchView.setQuery("", false)
                filterApp("")
                if (it.isNotEmpty()) {
                    viewBinding.stateView.showContent()
                } else {
                    viewBinding.stateView.showEmpty()
                }
            }
        }
    }

    private fun loadAppList() {
        viewBinding.stateView.showLoading()
        viewModel.getInstallAppList(currentUserID())
    }

    private fun filterApp(newText: String) {
        val newList = this.appList.filter {
            it.name.contains(newText, true) or it.packageName.contains(newText, true)
        }
        mAdapter.setItems(newList)
    }

    override fun onBackPressed() {
        if (viewBinding.searchView.isSearchOpen) {
            viewBinding.searchView.closeSearch()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_search, menu)
        val item = menu!!.findItem(R.id.list_search)
        viewBinding.searchView.setMenuItem(item)
        return true
    }


    companion object {
        fun start(context: Context) {
            val intent = Intent(context, FakeCameraManagerActivity::class.java)
            context.startActivity(intent)
        }
    }
}
