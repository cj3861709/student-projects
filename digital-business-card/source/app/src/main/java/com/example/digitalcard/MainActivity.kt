// 应用入口Activity，启动时初始化CardViewModel并加载Compose UI（CardApp）
package com.example.digitalcard

import android.os.Bundle                     // Activity生命周期回调参数
import androidx.activity.ComponentActivity    // Compose兼容的Activity基类
import androidx.activity.compose.setContent   // 在Compose中设置Content的扩展函数
import androidx.activity.enableEdgeToEdge     // 启用边到边显示（状态栏/导航栏沉浸）
import androidx.lifecycle.viewmodel.compose.viewModel  // Compose中获得ViewModel
import com.example.digitalcard.ui.CardApp     // 应用的根Composable组件
import com.example.digitalcard.viewmodel.CardViewModel  // 业务逻辑ViewModel

/**
 * 应用主Activity
 * 
 * 职责：作为应用唯一入口，负责初始化ViewModel并加载Compose UI。
 * 采用单一Activity架构，所有页面切换由CardApp内部的isEditing状态控制，
 * 无需多Activity或Fragment。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边到边显示，让内容延伸到状态栏和导航栏区域
        enableEdgeToEdge()
        // 设置Compose UI作为Activity的内容
        setContent {
            // 通过Android系统自动创建ViewModel（跨屏幕旋转保持数据）
            val viewModel: CardViewModel = viewModel()
            // 传入Context初始化ViewModel内部的数据仓库（DataStore）
            viewModel.init(this)
            // 加载根Composable组件，由它根据isEditing状态决定显示编辑页或预览页
            CardApp(viewModel)
        }
    }
}
