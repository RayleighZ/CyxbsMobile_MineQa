@file:Suppress("UNCHECKED_CAST")

package com.mredrock.cyxbs.main.ui

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.fragment.app.commit
import androidx.lifecycle.Observer
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mredrock.cyxbs.api.account.IAccountService
import com.mredrock.cyxbs.api.main.IMainService
import com.mredrock.cyxbs.api.update.AppUpdateStatus
import com.mredrock.cyxbs.api.update.IAppUpdateService
import com.mredrock.cyxbs.common.BaseApp
import com.mredrock.cyxbs.common.bean.LoginConfig
import com.mredrock.cyxbs.common.config.*
import com.mredrock.cyxbs.common.event.LoadCourse
import com.mredrock.cyxbs.common.event.NotifyBottomSheetToExpandEvent
import com.mredrock.cyxbs.common.event.RefreshQaEvent
import com.mredrock.cyxbs.common.mark.ActionLoginStatusSubscriber
import com.mredrock.cyxbs.common.mark.EventBusLifecycleSubscriber
import com.mredrock.cyxbs.common.network.ApiGenerator
import com.mredrock.cyxbs.common.service.ServiceManager
import com.mredrock.cyxbs.common.ui.BaseViewModelActivity
import com.mredrock.cyxbs.common.utils.LogUtils
import com.mredrock.cyxbs.common.utils.debug
import com.mredrock.cyxbs.common.utils.extensions.*
import com.mredrock.cyxbs.main.MAIN_MAIN
import com.mredrock.cyxbs.main.R
import com.mredrock.cyxbs.main.adapter.MainAdapter
import com.mredrock.cyxbs.main.components.DebugDataDialog
import com.mredrock.cyxbs.main.network.ApiService
import com.mredrock.cyxbs.main.utils.BottomNavigationHelper
import com.mredrock.cyxbs.main.utils.isDownloadSplash
import com.mredrock.cyxbs.main.viewmodel.MainViewModel
import com.umeng.analytics.MobclickAgent
import com.umeng.message.inapp.InAppMessageManager
import kotlinx.android.synthetic.main.main_activity_main.*
import kotlinx.android.synthetic.main.main_bottom_nav.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@Route(path = MAIN_MAIN)
class MainActivity : BaseViewModelActivity<MainViewModel>(),
        EventBusLifecycleSubscriber, ActionLoginStatusSubscriber {


    override val loginConfig = LoginConfig(
            isWarnUser = false,
            isCheckLogin = true
    )

    private lateinit var mainService: IMainService

    /**
     * ????????????????????????????????????viewModel,?????????????????????activity????????????
     * ?????????activity??????????????????????????????????????????activity??????????????????viewModel??????
     */
    private var isLoadCourse = true
    var lastState = BottomSheetBehavior.STATE_COLLAPSED

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    private lateinit var bottomHelper: BottomNavigationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.MainActivityTheme)//??????????????????????????????WindowBackground????????????
        super.onCreate(savedInstanceState)
        // ???????????????mainActivity????????????dataBinding????????????????????????????????????
        setContentView(R.layout.main_activity_main)
    }


    override fun initPage(isLoginElseTourist: Boolean, savedInstanceState: Bundle?) {
        /**
         * ???????????????????????????????????????????????????launcher??????->???home->??????launcher???????????????????????????
         * flag???????????????this
         * ???????????????SplashActivity?????????MainActivity???lunchMode???singleTas?????????????????????????????????
         * ?????????singleTop
         * @see android.content.Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
         */
        if ((intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            finish()
            return
        }
        checkSplash()
        InAppMessageManager.getInstance(BaseApp.context).showCardMessage(this, "???????????????") {} //??????????????????????????????????????????????????????
        mainService = ServiceManager.getService(IMainService::class.java)//????????????????????????
        viewModel.startPage.observe(this, Observer { starPage -> viewModel.initStartPage(starPage) })
        initUpdate()//?????????app????????????
        initBottom()//????????????????????????
        initBottomSheetBehavior()//?????????????????????BottomSheet??????
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (referrer != null && referrer?.toString() == "android-app://com.mredrock.cyxbs") {
                //???????????????????????????????????????????????????????????????
                viewModel.checkBindingEmail(ServiceManager.getService(IAccountService::class.java).getUserService().getStuNum()) {
                    val bindingEmailDialog = Dialog(this, R.style.transparent_dialog)
                    bindingEmailDialog.setContentView(R.layout.main_dialog_bind_email)
                    val confirm = bindingEmailDialog.findViewById<AppCompatButton>(R.id.main_bt_bind_email_confirm)
                    val cancel = bindingEmailDialog.findViewById<AppCompatButton>(R.id.main_bt_bind_email_cancel)
                    confirm.setOnClickListener {
                        //???????????????????????????
                        ARouter.getInstance().build(MINE_BIND_EMAIL).navigation()
                        bindingEmailDialog.dismiss()
                    }
                    cancel.setOnClickListener {
                        bindingEmailDialog.dismiss()
                    }
                    bindingEmailDialog.show()
                }
            }
        }
    }


    private fun initUpdate() {
        ServiceManager.getService(IAppUpdateService::class.java).apply {
            getUpdateStatus().observe {
                when (it) {
                    AppUpdateStatus.UNCHECK -> checkUpdate()
                    AppUpdateStatus.DATED -> noticeUpdate(this@MainActivity)
                    AppUpdateStatus.TO_BE_INSTALLED -> installUpdate(this@MainActivity)
                    else -> Unit
                }
            }
        }
    }

    private fun initBottomSheetBehavior() {
        bottomSheetBehavior = BottomSheetBehavior.from(course_bottom_sheet_content)
        /**
         * ????????????[android:fitsSystemWindows="true"]????????????????????????????????????
         * ??????????????????????????????????????????????????????
         * ????????????CoordinatorLayout?????????fitsSystemWindows???BottomSheet??????????????????activity
         * ???????????????fitsSystemWindows???????????????
         */

        course_bottom_sheet_content.topPadding = course_bottom_sheet_content.topPadding + getStatusBarHeight()
        bottomSheetBehavior.peekHeight = bottomSheetBehavior.peekHeight + course_bottom_sheet_content.topPadding
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                mainService.obtainBottomSheetStateLiveData().value = slideOffset
                if (main_view_pager.currentItem != 1 && slideOffset >= 0)
                    ll_nav_main_container.translationY = ll_nav_main_container.height * slideOffset
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (!ServiceManager.getService(IAccountService::class.java).getVerifyService().isLogin() && newState == BottomSheetBehavior.STATE_DRAGGING) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    return
                }
                //????????????????????????????????????????????????????????????
                if (isLoadCourse && bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
                        && lastState != BottomSheetBehavior.STATE_EXPANDED && !viewModel.isCourseDirectShow) {
                    EventBus.getDefault().post(LoadCourse())
                    isLoadCourse = false
                }
                //?????????Bottom?????????????????????????????????????????????????????????
                lastState = when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> BottomSheetBehavior.STATE_EXPANDED
                    BottomSheetBehavior.STATE_COLLAPSED -> BottomSheetBehavior.STATE_COLLAPSED
                    else -> lastState
                }
            }
        })
    }

    override fun onBackPressed() {
        //??????????????????????????????
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            moveTaskToBack(true)
        }
    }

    private fun initBottom() {
        // ViewPager2????????????OFFSCREEN_PAGE_LIMIT_DEFAULT??????????????????
        main_view_pager.adapter = MainAdapter(this)
        main_view_pager.isUserInputEnabled = false
        ll_nav_main_container.onTouch { _, _ -> }//????????????????????????????????????View????????????????????????
        //?????????????????????????????????
        bottomHelper = BottomNavigationHelper(arrayOf(explore, qa, mine)) {
            MobclickAgent.onEvent(this, CyxbsMob.Event.BOTTOM_TAB_CLICK, mutableMapOf(
                    Pair(CyxbsMob.Key.TAB_INDEX, it.toString())
            ))
            if (it == 1 && bottomHelper.peeCheckedItemPosition == 1)
                EventBus.getDefault().post(RefreshQaEvent())

            when(it){
                0 ->{
                    //??????????????????
                }
                1->{
                    //?????????????????????bottomSheet????????????????????????z?????????
                    bottomSheetBehavior.isHideable = true
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    ll_nav_main_container.elevation = BaseApp.context.dp2px(4f).toFloat()
                    //??????Tab????????????
                    if (bottomHelper.peeCheckedItemPosition == 1) EventBus.getDefault().post(RefreshQaEvent())
                }
                2->{
                    bottomSheetBehavior.isHideable = false
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    ll_nav_main_container.elevation = 0f
                }
            }
            it.takeUnless { it == 0 }?.apply {
                main_view_pager.setCurrentItem(this, false)
            }
        }

        // ??????????????????????????????
        debug {
            mine.setOnLongClickListener {
                DebugDataDialog(this).show()
                true
            }
        }
    }


    /**
     * ?????????????????????
     */
    private fun checkSplash() {
        viewModel.splashVisibility.observe(this, Observer {
            main_activity_splash_viewStub.visibility = it
        })
        //?????????????????????Splash??????????????????????????????
        if (isDownloadSplash(this@MainActivity)) {
            if (!ServiceManager.getService(IAccountService::class.java).getVerifyService().isLogin() && !ServiceManager.getService(IAccountService::class.java).getVerifyService().isTouristMode()) {
                //?????????????????????????????????????????????????????????
                //??????????????????????????????onCreate()??????????????????fragment???onStart()???????????????Activity???MainActivity????????????fragment??????activity
                return
            }
            main_activity_splash_viewStub.onTouch { _, _ -> }//??????????????????
            viewModel.splashVisibility.value = View.VISIBLE//?????????????????????
            supportFragmentManager.beginTransaction().replace(R.id.main_activity_splash_viewStub, SplashFragment()).commit()
        }
        //???????????????????????????????????????????????????????????????
        viewModel.getStartPage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(BOTTOM_SHEET_STATE, bottomSheetBehavior.state)
        outState.putInt(NAV_SELECT, bottomHelper.peeCheckedItemPosition)
    }

    /**
     * ????????????????????????????????????????????????????????????????????????????????????????????????
     * @param savedInstanceState ????????????savedInstanceState???????????????????????????
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        bottomHelper.selectTab(savedInstanceState.getInt(NAV_SELECT))
    }

    /**
     * ??????bottomSheet??????????????????????????????????????????BottomSheet
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun acceptNotifyBottomSheetToExpandEvent(notifyBottomSheetToExpandEvent: NotifyBottomSheetToExpandEvent) {
        if (notifyBottomSheetToExpandEvent.isToExpand) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }


    companion object {
        const val BOTTOM_SHEET_STATE = "BOTTOM_SHEET_STATE"
        const val NAV_SELECT = "NAV_SELECT"
        const val SPLASH_PHOTO_NAME = "splash_photo.jpg"
        const val SPLASH_PHOTO_LOCATION = "splash_store_location"
        const val FAST = "com.mredrock.cyxbs.action.COURSE"
    }
}
