// 视图管理类，管理名片信息状态、模板切换、二维码生成、数据持久化等业务逻辑
package com.example.digitalcard.viewmodel

import android.content.Context           // Android上下文，用于初始化DataStore
import android.graphics.Bitmap          // 二维码Bitmap
import androidx.compose.runtime.getValue        // Compose状态读取委托
import androidx.compose.runtime.mutableStateOf  // Compose可变状态（驱动UI重组）
import androidx.compose.runtime.setValue        // Compose状态写入委托
import androidx.lifecycle.ViewModel             // Android架构组件ViewModel基类
import androidx.lifecycle.viewModelScope        // ViewModel协程作用域（自动跟随生命周期取消）
import com.example.digitalcard.data.CardRepository      // 数据仓库（DataStore持久化）
import com.example.digitalcard.model.CardInfo           // 名片数据模型
import com.example.digitalcard.util.QrCodeGenerator     // 二维码生成工具
import kotlinx.coroutines.flow.first    // 收集Flow的第一个值（用于一次性读取）
import kotlinx.coroutines.launch        // 启动协程

/**
 * 名片业务逻辑的ViewModel
 *
 * 作为 MVVM 架构中的 ViewModel 层，负责：
 * 1. 持有所有UI状态（cardInfo, qrBitmap等），通过 mutableStateOf 驱动 Compose 重组
 * 2. 协调数据层（CardRepository）和UI层（EditScreen/PreviewScreen）
 * 3. 管理名片编辑、模板切换、二维码生成等业务操作
 *
 * 使用 mutableStateOf 而非 LiveData/Flow，因为它是 Compose 原生状态管理，
 * 值改变时自动触发重组（Recomposition），代码更简洁。
 */
class CardViewModel : ViewModel() {
    // 数据仓库，用于读写 DataStore（延迟初始化，在 init() 中创建）
    private lateinit var repository: CardRepository

    // ==================== UI 状态（Compose 驱动） ====================

    /** 当前名片数据，修改后自动触发 Compose 重组更新UI */
    var cardInfo by mutableStateOf(CardInfo())
        private set

    /** 生成的二维码 Bitmap，为 null 时不显示二维码 */
    var qrBitmap by mutableStateOf<Bitmap?>(null)
        private set

    /** 当前选中的模板索引（0=简约白, 1=科技蓝, 2=学术灰） */
    var selectedTemplateIndex by mutableStateOf(0)
        private set

    /** 页面模式：true=编辑页, false=预览页（类似单页应用路由） */
    var isEditing by mutableStateOf(true)  // 默认显示编辑页（首次使用时）

    // ==================== 初始化 ====================

    /**
     * 初始化 ViewModel
     * 需要在 Activity 中传入 Context 才能创建 DataStore
     * 使用 isInitialized 防止重复初始化
     *
     * @param context Android上下文，用于创建 CardRepository
     */
    fun init(context: Context) {
        if (!::repository.isInitialized) {
            repository = CardRepository(context)
            loadCardInfo()
        }
    }

    /**
     * 从 DataStore 加载上次保存的名片数据
     * 在协程中异步读取 Flow 的第一个值
     * 加载完成后自动生成二维码
     */
    private fun loadCardInfo() {
        viewModelScope.launch {
            val saved = repository.cardInfoFlow.first()
            cardInfo = saved
            selectedTemplateIndex = saved.templateIndex
            generateQrCode()
        }
    }

    // ==================== 编辑相关（更新单个字段） ====================

    /** 更新姓名 */
    fun updateName(name: String) {
        cardInfo = cardInfo.copy(name = name)
    }

    /** 更新头像URI（content:// 或 file:// 格式） */
    fun updateAvatar(uri: String) {
        cardInfo = cardInfo.copy(avatarUri = uri)
    }

    /** 更新职位 */
    fun updatePosition(position: String) {
        cardInfo = cardInfo.copy(position = position)
    }

    /** 更新电话号码 */
    fun updatePhone(phone: String) {
        cardInfo = cardInfo.copy(phone = phone)
    }

    /** 更新邮箱地址 */
    fun updateEmail(email: String) {
        cardInfo = cardInfo.copy(email = email)
    }

    /** 更新公司名称 */
    fun updateCompany(company: String) {
        cardInfo = cardInfo.copy(company = company)
    }

    /** 更新个人简介 */
    fun updateBio(bio: String) {
        cardInfo = cardInfo.copy(bio = bio)
    }

    /**
     * 保存名片信息到 DataStore 并切换至预览页
     *
     * 操作流程：
     * 1. 将当前选中的模板索引合并到 cardInfo
     * 2. 写入 DataStore 持久化
     * 3. 根据最新数据生成二维码
     * 4. isEditing = false 触发 CardApp 切换到预览页
     */
    fun saveCard() {
        viewModelScope.launch {
            cardInfo = cardInfo.copy(templateIndex = selectedTemplateIndex)
            repository.saveCardInfo(cardInfo)
            generateQrCode()
            isEditing = false
        }
    }

    // ==================== 模板切换 ====================

    /**
     * 切换名片模板风格
     * 同时更新 selectedTemplateIndex 和 cardInfo 中的模板索引，
     * 并持久化到 DataStore
     *
     * @param index 模板索引（0=简约白, 1=科技蓝, 2=学术灰）
     */
    fun switchTemplate(index: Int) {
        selectedTemplateIndex = index
        cardInfo = cardInfo.copy(templateIndex = index)
        viewModelScope.launch {
            repository.saveTemplateIndex(index)
        }
    }

    // ==================== QR 码生成 ====================

    /**
     * 生成二维码 Bitmap
     * 将 cardInfo 转为 vCard 格式字符串，通过 QrCodeGenerator 编码为二维码
     * 生成的 Bitmap 存储在 qrBitmap 中，供预览页显示
     */
    private fun generateQrCode() {
        val vCard = cardInfo.toVCard()
        qrBitmap = QrCodeGenerator.generateQrCode(vCard)
    }

    // ==================== 页面导航 ====================

    /** 从预览页切换到编辑页（点击编辑按钮时调用） */
    fun startEditing() {
        isEditing = true
    }

    /**
     * 取消编辑，从编辑页返回预览页
     * 会从 DataStore 重新加载原始数据，丢弃编辑中的修改
     * （即"撤销"当前编辑）
     */
    fun cancelEditing() {
        viewModelScope.launch {
            val saved = repository.cardInfoFlow.first()
            cardInfo = saved
            selectedTemplateIndex = saved.templateIndex
            isEditing = false
        }
    }
}
