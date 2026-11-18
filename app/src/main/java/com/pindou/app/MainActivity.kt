package com.pindou.app

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pindou.app.core.display.RefreshRateController
import com.pindou.app.ui.AppViewModel
import com.pindou.app.ui.PindouApp
import com.pindou.app.ui.RefreshRatePump
import com.pindou.app.ui.motion.ProvidePindouMotion
import com.pindou.app.ui.theme.PindouTheme
import com.pindou.app.ui.theme.AppThemes

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    // 系统设置里的「移除动画」会改 Animator duration scale；
    // 回到前台时重读，Compose 动效和系统开关保持同步。
    private var systemMotionEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        systemMotionEnabled = ValueAnimator.areAnimatorsEnabled()
        enableEdgeToEdge()
        RefreshRateController.applyWindow(this, vm.settings.refreshMode)
        setContent {
            val dark = when (vm.settings.themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            // 设置里改完立刻生效，不用重进页面
            LaunchedEffect(vm.settings.refreshMode) {
                RefreshRateController.applyWindow(this@MainActivity, vm.settings.refreshMode)
            }
            val skin = AppThemes.byId(vm.settings.themeSkin)
            ProvidePindouMotion(systemMotionEnabled) {
                PindouTheme(appTheme = skin, darkTheme = dark) {
                    Box(Modifier.fillMaxSize()) {
                        PindouApp(vm)
                        // 换档要重建 Surface：已经出过帧的 producer 改 setFrameRate 不生效
                        key(vm.settings.refreshMode) {
                            RefreshRatePump(vm.settings.refreshMode)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        systemMotionEnabled = ValueAnimator.areAnimatorsEnabled()
        // 切后台时系统会把模式改回去，回前台补写一次
        RefreshRateController.applyWindow(this, vm.settings.refreshMode)
    }
}
