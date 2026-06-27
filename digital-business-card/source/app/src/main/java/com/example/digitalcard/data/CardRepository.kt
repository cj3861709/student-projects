// 数据仓库层，使用Jetpack DataStore持久化名片信息到本地，支持Flow实时观察
package com.example.digitalcard.data

import android.content.Context               // Android上下文，用于获取ApplicationContext
import androidx.datastore.core.DataStore      // DataStore核心接口
import androidx.datastore.preferences.core.Preferences      // DataStore的键值对类型
import androidx.datastore.preferences.core.edit             // 事务性编辑DataStore的扩展函数
import androidx.datastore.preferences.core.intPreferencesKey  // Int类型偏好键
import androidx.datastore.preferences.core.stringPreferencesKey // String类型偏好键
import androidx.datastore.preferences.preferencesDataStore    // 创建DataStore实例的委托
import com.example.digitalcard.model.CardInfo                 // 名片数据模型
import kotlinx.coroutines.flow.Flow      // 响应式数据流
import kotlinx.coroutines.flow.map      // 转换Flow中的值

// 模块级 DataStore 扩展属性
// 使用 Kotlin 委托创建单例 DataStore，文件名为 "card_settings.preferences_pb"
// preferencesDataStore 是线程安全的，只会创建一个实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "card_settings")

/**
 * 名片数据仓库
 *
 * 职责：封装 DataStore 的读写逻辑，对外提供 Flow 形式的响应式数据访问。
 * 采用 Jetpack DataStore（Preferences 版本）替代传统的 SharedPreferences，
 * 优势：
 * - 基于 Flow 的响应式 API，可观察数据变化
 * - 异步操作，不阻塞主线程
 * - 自动处理线程安全和数据一致性
 *
 * @param context 用于获取 ApplicationContext 以创建 DataStore（避免内存泄漏）
 */
class CardRepository(context: Context) {

    // 使用 ApplicationContext 防止 Activity 级别的内存泄漏
    private val appContext = context.applicationContext

    companion object {
        // ========== DataStore 键定义 ==========
        // 每个键对应 CardInfo 中的一个字段，使用类型安全的 PreferencesKey
        private val KEY_NAME = stringPreferencesKey("card_name")
        private val KEY_AVATAR = stringPreferencesKey("card_avatar")
        private val KEY_POSITION = stringPreferencesKey("card_position")
        private val KEY_PHONE = stringPreferencesKey("card_phone")
        private val KEY_EMAIL = stringPreferencesKey("card_email")
        private val KEY_COMPANY = stringPreferencesKey("card_company")
        private val KEY_BIO = stringPreferencesKey("card_bio")
        private val KEY_TEMPLATE = intPreferencesKey("card_template")
    }

    // ==================== 读取数据 ====================

    /**
     * 以 Flow 形式提供名片数据
     *
     * DataStore.data 本身是一个 Flow<Preferences>，每次文件变更都会自动发射新值。
     * 使用 map 操作符将原始 Preferences 转换为 CardInfo 数据模型。
     * 所有字段都有默认值空字符串或0，保证不会出现 null 安全问题。
     */
    val cardInfoFlow: Flow<CardInfo> = appContext.dataStore.data.map { prefs ->
        CardInfo(
            name = prefs[KEY_NAME] ?: "",
            avatarUri = prefs[KEY_AVATAR] ?: "",
            position = prefs[KEY_POSITION] ?: "",
            phone = prefs[KEY_PHONE] ?: "",
            email = prefs[KEY_EMAIL] ?: "",
            company = prefs[KEY_COMPANY] ?: "",
            bio = prefs[KEY_BIO] ?: "",
            templateIndex = prefs[KEY_TEMPLATE] ?: 0
        )
    }

    // ==================== 写入数据 ====================

    /**
     * 保存完整的名片信息
     *
     * DataStore.edit 提供事务性写入：在 lambda 中可多次写入 Preferences，
     * 最终一次性原子提交到磁盘。
     * 这是一个 suspend 函数，必须在协程中调用。
     *
     * @param cardInfo 要保存的名片数据模型
     */
    suspend fun saveCardInfo(cardInfo: CardInfo) {
        appContext.dataStore.edit { prefs ->
            // 将 CardInfo 的每个字段逐个写入对应的偏好键
            prefs[KEY_NAME] = cardInfo.name
            prefs[KEY_AVATAR] = cardInfo.avatarUri
            prefs[KEY_POSITION] = cardInfo.position
            prefs[KEY_PHONE] = cardInfo.phone
            prefs[KEY_EMAIL] = cardInfo.email
            prefs[KEY_COMPANY] = cardInfo.company
            prefs[KEY_BIO] = cardInfo.bio
            prefs[KEY_TEMPLATE] = cardInfo.templateIndex
        }
    }

    /**
     * 单独保存模板索引
     *
     * 与 saveCardInfo 分离的原因：切换模板时只需更新一个字段，
     * 避免读取完整 cardInfo 的开销。
     * 内部仍然使用 DataStore.edit 实现原子写入。
     *
     * @param index 模板索引（0=简约白, 1=科技蓝, 2=学术灰）
     */
    suspend fun saveTemplateIndex(index: Int) {
        appContext.dataStore.edit { prefs ->
            prefs[KEY_TEMPLATE] = index
        }
    }
}
