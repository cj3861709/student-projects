// 根Composable组件，根据isEditing状态切换编辑页面/预览页面
package com.example.digitalcard.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.digitalcard.ui.screen.EditScreen   // 名片编辑页面
import com.example.digitalcard.ui.screen.PreviewScreen // 名片预览页面
import com.example.digitalcard.ui.theme.EmptyActivityTheme  // 应用主题
import com.example.digitalcard.viewmodel.CardViewModel  // 业务逻辑ViewModel

/**
 * 应用的根Composable组件
 *
 * 采用类似"单页应用"的模式，通过 ViewModel 中的 isEditing 状态
 * 决定当前显示编辑页面还是预览页面，无需 Navigation 组件。
 *
 * @param viewModel 持有所有业务状态和逻辑的ViewModel，默认由Compose自动创建
 */
@Composable
fun CardApp(viewModel: CardViewModel = viewModel()) {
    // 应用主题包裹，确保所有子组件使用统一的颜色和字体风格
    EmptyActivityTheme {
        if (viewModel.isEditing) {
            // 编辑模式：显示编辑页面，用户可填写信息、选择头像
            EditScreen(
                viewModel = viewModel,
                onSave = { viewModel.saveCard() },     // 保存按钮回调：持久化数据并切换至预览页
                onCancel = { viewModel.cancelEditing() } // 取消按钮回调：不保存直接返回预览
            )
        } else {
            // 预览模式：显示名片预览，用户可切换模板/保存图片/分享
            PreviewScreen(
                viewModel = viewModel,
                onEdit = { viewModel.startEditing() }  // 编辑按钮回调：切换至编辑页
            )
        }
    }
}
