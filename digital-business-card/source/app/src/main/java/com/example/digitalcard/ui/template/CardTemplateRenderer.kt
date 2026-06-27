// Canvas名片渲染器，将名片信息按模板绘制为Bitmap，用于保存图片和分享
package com.example.digitalcard.ui.template

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.digitalcard.model.CardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 将名片信息渲染为 Bitmap（用于分享/保存图片）
 *
 * 使用 Android Canvas 直接绘制，不依赖 ComposeView / Choreographer。
 * 所有渲染完全同步，可在 IO 线程执行。
 * 设计为单例（object），全局共享无状态渲染方法。
 */
object CardTemplateRenderer {

    // ==================== 布局常量（像素单位） ====================
    /** 页面四边内边距 */
    private const val PADDING = 48f
    /** 头像圆形直径 */
    private const val AVATAR_SIZE = 120f
    /** 二维码方形边长 */
    private const val QR_SIZE = 160f
    /** 姓名字号（最大） */
    private const val FONT_SIZE_NAME = 46f
    /** 职位的字号 */
    private const val FONT_SIZE_POSITION = 28f
    /** 联系信息（电话/邮箱/公司）的字号 */
    private const val FONT_SIZE_INFO = 26f
    /** 个人简介字号（最小） */
    private const val FONT_SIZE_BIO = 22f
    /** 联系信息左侧图标的字号 */
    private const val ICON_SIZE = 26f
    /** 同行与行之间的垂直间距 */
    private const val LINE_SPACING = 6f
    /** 不同区块（头像→姓名→信息→简介→二维码）之间的垂直间距 */
    private const val SECTION_SPACING = 16f

    // ==================== 颜色常量 ====================
    /** 头像无图片时的灰色圆形背景 */
    private val ROUNDED_RADIUS = 24f
    /** 头像无图片时的占位灰色背景 */
    private val AVATAR_BG_COLOR = Color.parseColor("#E0E0E0")
    /** 主要文字颜色（姓名、电话、邮箱） */
    private val TEXT_COLOR = Color.parseColor("#212121")
    /** 次要文字颜色（职位） */
    private val TEXT_SECONDARY = Color.parseColor("#757575")
    /** 浅色文字颜色（个人简介） */
    private val TEXT_LIGHT = Color.parseColor("#9E9E9E")
    /** 分割线颜色（浅灰） */
    private val DIVIDER_COLOR = Color.parseColor("#EEEEEE")

    // ==================== 公开 API ====================

    /**
     * 渲染名片为 Bitmap（公开入口，在 IO 协程中执行）
     *
     * @param context Android Context，用于通过 ContentResolver 加载头像文件
     * @param cardInfo 需要渲染的名片数据模型
     * @param qrBitmap 二维码 Bitmap（可选），为 null 时不渲染二维码区域
     * @param targetWidth 输出宽度（像素），默认 1080，适配大多数手机分享尺寸
     * @return 渲染好的 Bitmap，若发生异常返回 null
     */
    suspend fun renderCardToBitmap(
        context: Context,
        cardInfo: CardInfo,
        qrBitmap: Bitmap?,
        targetWidth: Int = 1080
    ): Bitmap? {
        return try {
            // 切换到 IO 线程执行耗时渲染，避免阻塞主线程
            withContext(Dispatchers.IO) {
                renderOnCanvas(context, cardInfo, qrBitmap, targetWidth)
            }
        } catch (e: Exception) {
            // 兜底异常捕获，防止协程崩溃
            e.printStackTrace()
            null
        }
    }

    // ==================== 核心渲染逻辑 ====================

    /**
     * 使用 Canvas 直接绘制名片（根据 cardInfo.templateIndex 选择模板风格）
     *
     * 支持三种模板风格：
     *   0 - 简约白：纯白背景，黑色文字，居中布局
     *   1 - 科技蓝：蓝色渐变背景，白色文字，左对齐布局
     *   2 - 学术灰：浅灰背景，深灰文字，左右分区布局
     *
     * @param context Android Context
     * @param cardInfo 名片数据（含 templateIndex）
     * @param qrBitmap 二维码 Bitmap
     * @param targetWidth 输出宽度（像素）
     */
    private fun renderOnCanvas(
        context: Context,
        cardInfo: CardInfo,
        qrBitmap: Bitmap?,
        targetWidth: Int
    ): Bitmap = when (cardInfo.templateIndex) {
        0 -> renderSimpleWhite(context, cardInfo, qrBitmap, targetWidth)
        1 -> renderTechBlue(context, cardInfo, qrBitmap, targetWidth)
        2 -> renderAcademicGray(context, cardInfo, qrBitmap, targetWidth)
        else -> renderSimpleWhite(context, cardInfo, qrBitmap, targetWidth)
    }
    
    // ==================== 子模板渲染方法 ====================

    /**
     * 简约白模板：纯白背景，黑色文字，居中布局
     *
     * 布局结构（从上到下）：
     *   ① 圆形头像
     *   ② 姓名（加粗居中）
     *   ③ 职位（灰色居中）
     *   ④ 分割线
     *   ⑤ 联系信息（电话、邮箱、公司，带图标）
     *   ⑥ 个人简介（多行文字，自动换行）
     *   ⑦ 二维码（白色圆角背景框）
     */
    private fun renderSimpleWhite(
        context: Context,
        cardInfo: CardInfo,
        qrBitmap: Bitmap?,
        targetWidth: Int
    ): Bitmap {
        // 内容区域宽度（减去左右内边距）
        val contentWidth = targetWidth - (PADDING * 2).toInt()

        // ========== 第一步：测量总高度 ==========
        var y = PADDING

        // 头像区域（圆形直径 + 底部间距）
        y += AVATAR_SIZE + 16f

        // 姓名区域
        if (cardInfo.name.isNotBlank()) {
            y += FONT_SIZE_NAME + LINE_SPACING
        }

        // 职位区域（有职位则多一段区块间距，无职位但有姓名则缩小间距）
        if (cardInfo.position.isNotBlank()) {
            y += FONT_SIZE_POSITION + LINE_SPACING + SECTION_SPACING
        } else if (cardInfo.name.isNotBlank()) {
            y += SECTION_SPACING
        }

        // 联系信息区域（计算实际非空行数 × 行高）
        val infoCount = countNonBlank(cardInfo.phone, cardInfo.email, cardInfo.company)
        if (infoCount > 0) {
            y += SECTION_SPACING / 2 // 分割线前间距
            y += infoCount * (FONT_SIZE_INFO + LINE_SPACING * 2)
            y += SECTION_SPACING
        }

        // 个人简介区域（使用 StaticLayout 测量多行文本高度）
        if (cardInfo.bio.isNotBlank()) {
            y += SECTION_SPACING + 8f
            val bioLayout = makeStaticLayout(cardInfo.bio, contentWidth, FONT_SIZE_BIO, TEXT_LIGHT)
            y += bioLayout.height.toFloat() + SECTION_SPACING
        }

        // 二维码区域
        if (qrBitmap != null) {
            y += QR_SIZE + PADDING
        }

        y += PADDING
        // 保证最小高度不低于宽度的 3/5，避免名片比例过于细长
        val totalHeight = y.toInt().coerceAtLeast(targetWidth * 3 / 5)

        // ========== 第二步：创建 Bitmap 并绘制 ==========
        val bitmap = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 纯白背景色
        canvas.drawColor(Color.WHITE)

        // 加载头像 Bitmap（从 URI 或文件路径）
        val avatarBitmap = loadAvatarBitmap(context, cardInfo.avatarUri)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val infoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        var drawY = PADDING

        // ===== ① 绘制头像 =====
        val centerX = targetWidth / 2f
        drawAvatar(canvas, avatarBitmap, cardInfo.name, centerX, drawY + AVATAR_SIZE / 2)

        drawY += AVATAR_SIZE + 16f

        // ===== ② 绘制姓名 =====
        if (cardInfo.name.isNotBlank()) {
            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.textSize = FONT_SIZE_NAME
            textPaint.color = TEXT_COLOR
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD // 加粗
            canvas.drawText(cardInfo.name, centerX, drawY + FONT_SIZE_NAME, textPaint)
            drawY += FONT_SIZE_NAME + LINE_SPACING
        }

        // ===== ③ 绘制职位 =====
        if (cardInfo.position.isNotBlank()) {
            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.textSize = FONT_SIZE_POSITION
            textPaint.color = TEXT_SECONDARY
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(cardInfo.position, centerX, drawY + FONT_SIZE_POSITION, textPaint)
            drawY += FONT_SIZE_POSITION + LINE_SPACING + SECTION_SPACING
        } else if (cardInfo.name.isNotBlank()) {
            drawY += SECTION_SPACING
        }

        // ===== ④ 绘制分割线 =====
        val dividerPaint = Paint().apply {
            color = DIVIDER_COLOR
            strokeWidth = 1.5f
        }
        if (infoCount > 0) {
            drawY += SECTION_SPACING / 2
            canvas.drawLine(PADDING * 2, drawY, targetWidth - PADDING * 2, drawY, dividerPaint)
            drawY += SECTION_SPACING
        }

        // ===== ⑤ 绘制联系信息（电话/邮箱/公司） =====
        val iconLeft = PADDING * 2          // 图标 X 坐标
        val textLeft = iconLeft + ICON_SIZE + 16f  // 文字的 X 坐标（图标右侧）
        infoPaint.reset()
        infoPaint.isAntiAlias = true
        infoPaint.textSize = FONT_SIZE_INFO
        infoPaint.color = TEXT_COLOR

        // 逐行绘制，每行由「图标 + 文字」组成，返回下一行的起始 Y 坐标
        drawY = drawInfoLine(canvas, "\u260E", cardInfo.phone, iconLeft, textLeft, drawY, TEXT_COLOR)
        drawY = drawInfoLine(canvas, "\u2709", cardInfo.email, iconLeft, textLeft, drawY, TEXT_COLOR)
        drawY = drawInfoLine(canvas, "\u2302", cardInfo.company, iconLeft, textLeft, drawY, TEXT_SECONDARY)

        // ===== ⑥ 绘制个人简介 =====
        if (cardInfo.bio.isNotBlank()) {
            drawY += SECTION_SPACING
            // 简介上方的分割线
            canvas.drawLine(PADDING * 2, drawY, targetWidth - PADDING * 2, drawY, dividerPaint)
            drawY += SECTION_SPACING + 8f

            // 使用 StaticLayout 实现多行自动换行
            val bioLayout = makeStaticLayout(cardInfo.bio, contentWidth, FONT_SIZE_BIO, TEXT_LIGHT)
            canvas.save()
            canvas.translate(PADDING, drawY)
            bioLayout.draw(canvas)  // StaticLayout 自行管理多行绘制
            canvas.restore()
            drawY += bioLayout.height.toFloat() + SECTION_SPACING
        }

        // ===== ⑦ 绘制二维码 =====
        if (qrBitmap != null) {
            drawY += PADDING
            val qrLeft = (targetWidth - QR_SIZE) / 2f
            // 白色圆角背景框，使二维码与白色背景略有区分（视觉层次）
            val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawRoundRect(qrLeft - 8f, drawY - 8f, qrLeft + QR_SIZE + 8f, drawY + QR_SIZE + 8f, 12f, 12f, qrBgPaint)
            // 二维码 Bitmap 直接缩放绘制到指定矩形区域内
            canvas.drawBitmap(qrBitmap, null, RectF(qrLeft, drawY, qrLeft + QR_SIZE, drawY + QR_SIZE), null)
        }

        return bitmap
    }

    /**
     * 科技蓝模板：蓝色渐变背景，白色/浅蓝文字，左对齐布局
     *
     * 布局结构（从上到下）：
     *   ① 顶部发光装饰线（青色）
     *   ② 头像 + 姓名、职位（Row 并排）
     *   ③ 半透明信息卡片（电话/邮箱/公司）
     *   ④ 个人简介
     *   ⑤ 二维码浮在右下角
     */
    private fun renderTechBlue(
        context: Context,
        cardInfo: CardInfo,
        qrBitmap: Bitmap?,
        targetWidth: Int
    ): Bitmap {
        val contentWidth = targetWidth - (PADDING * 2).toInt()

        // ========== 第一步：测量总高度 ==========
        var y = PADDING

        // 顶部装饰线
        y += 3f + 20f

        // 头像区域（圆形直径 + 底部间距）
        y += AVATAR_SIZE + 16f

        // 姓名区域
        if (cardInfo.name.isNotBlank()) {
            y += FONT_SIZE_NAME + LINE_SPACING
        }

        // 职位区域
        if (cardInfo.position.isNotBlank()) {
            y += FONT_SIZE_POSITION + LINE_SPACING + SECTION_SPACING
        } else if (cardInfo.name.isNotBlank()) {
            y += SECTION_SPACING
        }

        // 联系信息区域
        val infoCount = countNonBlank(cardInfo.phone, cardInfo.email, cardInfo.company)
        if (infoCount > 0) {
            y += SECTION_SPACING
            y += infoCount * (FONT_SIZE_INFO + LINE_SPACING * 2)
            y += SECTION_SPACING * 2  // 信息卡片的内边距
        }

        // 个人简介区域
        if (cardInfo.bio.isNotBlank()) {
            y += SECTION_SPACING
            val bioLayout = makeStaticLayout(cardInfo.bio, contentWidth, FONT_SIZE_BIO, Color.WHITE)
            y += bioLayout.height.toFloat() + SECTION_SPACING
        }

        // 二维码区域（右下角浮动）
        if (qrBitmap != null) {
            y += QR_SIZE / 2 + PADDING
        }

        y += PADDING
        val totalHeight = y.toInt().coerceAtLeast(targetWidth * 3 / 5)

        // ========== 第二步：创建 Bitmap 并绘制 ==========
        val bitmap = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 蓝色渐变背景
        val gradientPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, targetWidth.toFloat(), totalHeight.toFloat(),
                intArrayOf(Color.parseColor("#1A237E"), Color.parseColor("#283593"), Color.parseColor("#3949AB")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, targetWidth.toFloat(), totalHeight.toFloat(), gradientPaint)

        val avatarBitmap = loadAvatarBitmap(context, cardInfo.avatarUri)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        var drawY = PADDING

        // ===== ① 顶部装饰线 =====
        val accentPaint = Paint().apply {
            color = Color.parseColor("#00E5FF")
            strokeWidth = 3f
        }
        canvas.drawLine(PADDING, drawY, PADDING + targetWidth * 0.3f, drawY, accentPaint)
        drawY += 20f

        // ===== ② 绘制头像（左对齐） =====
        val avatarCX = PADDING + AVATAR_SIZE / 2
        drawAvatar(canvas, avatarBitmap, cardInfo.name, avatarCX, drawY + AVATAR_SIZE / 2)

        // ===== ③ 绘制姓名 + 职位（头像右侧） =====
        val textStartX = PADDING + AVATAR_SIZE + 16f
        if (cardInfo.name.isNotBlank()) {
            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.textSize = FONT_SIZE_NAME
            textPaint.color = Color.WHITE
            textPaint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(cardInfo.name, textStartX, drawY + FONT_SIZE_NAME, textPaint)
            drawY += FONT_SIZE_NAME + LINE_SPACING
        }
        // 职位
        if (cardInfo.position.isNotBlank()) {
            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.textSize = FONT_SIZE_POSITION
            textPaint.color = Color.parseColor("#90CAF9")
            canvas.drawText(cardInfo.position, textStartX, drawY + FONT_SIZE_POSITION, textPaint)
            drawY += FONT_SIZE_POSITION + LINE_SPACING + SECTION_SPACING
        } else if (cardInfo.name.isNotBlank()) {
            drawY += SECTION_SPACING
        }

        // ===== ④ 联系信息（半透明信息卡片） =====
        if (infoCount > 0) {
            drawY += SECTION_SPACING
            // 半透明背景卡片
            val cardBgPaint = Paint().apply {
                color = Color.parseColor("#1FFFFFFF")
                isAntiAlias = true
            }
            val cardTop = drawY - LINE_SPACING
            val infoHeight = infoCount * (FONT_SIZE_INFO + LINE_SPACING * 2) + SECTION_SPACING * 2
            canvas.drawRoundRect(
                PADDING, cardTop, targetWidth - PADDING, cardTop + infoHeight,
                ROUNDED_RADIUS, ROUNDED_RADIUS, cardBgPaint
            )

            val iconLeft = PADDING + SECTION_SPACING
            val textLeft = iconLeft + ICON_SIZE + 16f + SECTION_SPACING / 2
            val infoStartY = cardTop + SECTION_SPACING

            drawY = drawInfoLine(canvas, "\u260E", cardInfo.phone, iconLeft, textLeft, infoStartY, Color.parseColor("#90CAF9"))
            drawY = drawInfoLine(canvas, "\u2709", cardInfo.email, iconLeft, textLeft, drawY, Color.parseColor("#90CAF9"))
            drawY = drawInfoLine(canvas, "\u2302", cardInfo.company, iconLeft, textLeft, drawY, Color.parseColor("#90CAF9"))
            drawY = infoStartY + infoHeight
        }

        // ===== ⑤ 绘制个人简介 =====
        if (cardInfo.bio.isNotBlank()) {
            drawY += SECTION_SPACING
            val bioLayout = makeStaticLayout(cardInfo.bio, contentWidth, FONT_SIZE_BIO, Color.WHITE)
            val bioPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = FONT_SIZE_BIO
                this.color = Color.WHITE
                alpha = 204  // 0.8 opacity
            }
            canvas.save()
            canvas.translate(PADDING, drawY)
            bioLayout.draw(canvas)
            canvas.restore()
            drawY += bioLayout.height.toFloat() + SECTION_SPACING
        }

        // ===== ⑥ 绘制二维码（右下角） =====
        if (qrBitmap != null) {
            drawY += PADDING
            val qrLeft = targetWidth - PADDING - QR_SIZE
            val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                setShadowLayer(8f, 2f, 2f, Color.parseColor("#40000000"))
            }
            canvas.drawRoundRect(qrLeft - 6f, drawY - 6f, qrLeft + QR_SIZE + 6f, drawY + QR_SIZE + 6f, 8f, 8f, qrBgPaint)
            canvas.drawBitmap(qrBitmap, null, RectF(qrLeft, drawY, qrLeft + QR_SIZE, drawY + QR_SIZE), null)
        }

        return bitmap
    }

    /**
     * 学术灰模板：浅灰背景，深灰/蓝灰文字，左右分区布局
     *
     * 布局结构：
     *   ① 顶部三条装饰色条（深灰/蓝灰/灰蓝）
     *   ② 主体：左侧头像 + 姓名 + 职位 / 右侧公司 + 电话 + 邮箱 + 简介
     *   ③ 底部二维码居中
     */
    private fun renderAcademicGray(
        context: Context,
        cardInfo: CardInfo,
        qrBitmap: Bitmap?,
        targetWidth: Int
    ): Bitmap {
        val contentWidth = targetWidth - (PADDING * 2).toInt()

        // ========== 第一步：测量总高度 ==========
        var y = PADDING

        // 顶部装饰条
        y += 4f + 20f

        // 左侧头像区域
        y += AVATAR_SIZE + 8f

        // 左侧姓名
        var leftNameHeight = 0f
        if (cardInfo.name.isNotBlank()) {
            leftNameHeight = FONT_SIZE_NAME * 0.7f + LINE_SPACING
            y += leftNameHeight
        }

        // 左侧职位
        if (cardInfo.position.isNotBlank()) {
            y += FONT_SIZE_POSITION * 0.7f + LINE_SPACING
        }

        // 右侧公司
        if (cardInfo.company.isNotBlank()) {
            y += FONT_SIZE_INFO + LINE_SPACING
        }

        // 右侧电话、邮箱
        val rightInfoCount = countNonBlank(cardInfo.phone, cardInfo.email)
        if (rightInfoCount > 0) {
            y += rightInfoCount * (FONT_SIZE_INFO + LINE_SPACING * 2) + SECTION_SPACING
        }

        // 右侧简介
        if (cardInfo.bio.isNotBlank()) {
            val bioLayout = makeStaticLayout(cardInfo.bio, contentWidth / 2, FONT_SIZE_BIO, Color.parseColor("#607D8B"))
            y += bioLayout.height.toFloat() + SECTION_SPACING
        }

        // 二维码区域
        if (qrBitmap != null) {
            y += QR_SIZE + PADDING
        }

        y += PADDING
        val totalHeight = y.toInt().coerceAtLeast(targetWidth * 3 / 5)

        // ========== 第二步：创建 Bitmap 并绘制 ==========
        val bitmap = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 浅灰背景
        canvas.drawColor(Color.parseColor("#F5F5F5"))

        val avatarBitmap = loadAvatarBitmap(context, cardInfo.avatarUri)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        var drawY = PADDING

        // ===== ① 顶部装饰色条 =====
        val barWidth = (targetWidth - PADDING * 2) / 3f
        val barY = drawY
        canvas.drawRect(PADDING, barY, PADDING + barWidth, barY + 4f, Paint().apply { color = Color.parseColor("#37474F") })
        canvas.drawRect(PADDING + barWidth, barY, PADDING + barWidth * 2, barY + 4f, Paint().apply { color = Color.parseColor("#546E7A") })
        canvas.drawRect(PADDING + barWidth * 2, barY, PADDING + barWidth * 3, barY + 4f, Paint().apply { color = Color.parseColor("#78909C") })
        drawY += 20f

        // ===== ② 左侧头像 =====
        val leftCenterX = PADDING + 80f  // 左侧区域宽度 160dp
        drawAvatar(canvas, avatarBitmap, cardInfo.name, leftCenterX, drawY + AVATAR_SIZE / 2)
        drawY += AVATAR_SIZE + 8f

        // ===== ③ 左侧姓名（居中） =====
        if (cardInfo.name.isNotBlank()) {
            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.textSize = FONT_SIZE_NAME * 0.7f
            textPaint.color = Color.parseColor("#37474F")
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(cardInfo.name, leftCenterX, drawY + FONT_SIZE_NAME * 0.7f, textPaint)
            drawY += FONT_SIZE_NAME * 0.7f + LINE_SPACING

            textPaint.textAlign = Paint.Align.LEFT
        }

        // ===== ④ 左侧职位 =====
        if (cardInfo.position.isNotBlank()) {
            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.textSize = FONT_SIZE_POSITION * 0.6f
            textPaint.color = Color.parseColor("#78909C")
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(cardInfo.position, leftCenterX, drawY + FONT_SIZE_POSITION * 0.6f, textPaint)
            drawY += FONT_SIZE_POSITION * 0.6f + LINE_SPACING + SECTION_SPACING
            textPaint.textAlign = Paint.Align.LEFT
        }

        // 重置 drawY 到左侧最顶部，准备绘制右侧内容
        var rightY = PADDING + 24f  // 对齐装饰条下方

        // ===== ⑤ 右侧公司 =====
        val rightTextStartX = PADDING + 160f + 20f  // 左侧宽度160dp + 间隔
        val rightWidth = targetWidth - rightTextStartX - PADDING

        if (cardInfo.company.isNotBlank()) {
            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.textSize = FONT_SIZE_INFO
            textPaint.color = Color.parseColor("#455A64")
            textPaint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(cardInfo.company, rightTextStartX, rightY + FONT_SIZE_INFO, textPaint)
            rightY += FONT_SIZE_INFO + LINE_SPACING + 6f
        }

        // ===== ⑥ 右侧电话/邮箱 =====
        val infoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
            textSize = FONT_SIZE_INFO * 0.85f
            color = Color.parseColor("#455A64")
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            isAntiAlias = true
            textSize = FONT_SIZE_INFO * 0.7f
            color = Color.parseColor("#78909C")
        }
        if (cardInfo.phone.isNotBlank()) {
            canvas.drawText("电话:", rightTextStartX, rightY + FONT_SIZE_INFO, labelPaint)
            val phoneX = rightTextStartX + 70f
            canvas.drawText(cardInfo.phone, phoneX, rightY + FONT_SIZE_INFO, infoPaint)
            rightY += FONT_SIZE_INFO + LINE_SPACING * 2
        }
        if (cardInfo.email.isNotBlank()) {
            canvas.drawText("邮箱:", rightTextStartX, rightY + FONT_SIZE_INFO, labelPaint)
            val emailX = rightTextStartX + 70f
            canvas.drawText(cardInfo.email, emailX, rightY + FONT_SIZE_INFO, infoPaint)
            rightY += FONT_SIZE_INFO + LINE_SPACING * 2
        }

        // 对齐左右渲染位置
        drawY = Math.max(drawY, rightY)

        // ===== ⑦ 右侧个人简介 =====
        if (cardInfo.bio.isNotBlank()) {
            if (drawY > rightY) {
                // 左侧比右侧长，调整右侧起始位置
                rightY = drawY
            }
            drawY = rightY + SECTION_SPACING
            val bioPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = FONT_SIZE_BIO
                color = Color.parseColor("#607D8B")
            }
            val bioLayout = makeStaticLayout(cardInfo.bio, rightWidth.toInt(), FONT_SIZE_BIO, Color.parseColor("#607D8B"))
            canvas.save()
            canvas.translate(rightTextStartX, drawY)
            bioLayout.draw(canvas)
            canvas.restore()
            drawY += bioLayout.height.toFloat() + SECTION_SPACING
        }

        // ===== ⑧ 底部二维码 =====
        if (qrBitmap != null) {
            drawY += PADDING
            // 分割线
            val dividerPaint = Paint().apply {
                color = Color.parseColor("#E0E0E0")
                strokeWidth = 1f
            }
            canvas.drawLine(PADDING * 2, drawY, targetWidth - PADDING * 2, drawY, dividerPaint)
            drawY += SECTION_SPACING

            val qrLeft = (targetWidth - QR_SIZE) / 2f
            val qrBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawRoundRect(qrLeft - 8f, drawY - 8f, qrLeft + QR_SIZE + 8f, drawY + QR_SIZE + 8f, 12f, 12f, qrBgPaint)
            canvas.drawBitmap(qrBitmap, null, RectF(qrLeft, drawY, qrLeft + QR_SIZE, drawY + QR_SIZE), null)

            // "扫码保存"文字
            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.textSize = 18f
            textPaint.color = Color.parseColor("#9E9E9E")
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("扫码保存", targetWidth / 2f, drawY + QR_SIZE + 28f, textPaint)
        }

        return bitmap
    }

    // ==================== 辅助绘制方法 ====================

    /**
     * 绘制圆形头像
     *
     * 逻辑分支：
     *   - 有有效头像 Bitmap → 裁剪为圆形绘制
     *   - 无头像但有名 → 灰色圆形 + 白色首字母占位
     *   - 无头像也无名 → 仅灰色圆形
     * 最后统一绘制一圈浅灰色细边框增强立体感。
     *
     * @param cx 圆心 X 坐标（canvas 居中位置）
     * @param cy 圆心 Y 坐标
     */
    private fun drawAvatar(canvas: Canvas, avatarBitmap: Bitmap?, name: String, cx: Float, cy: Float) {
        val radius = AVATAR_SIZE / 2

        // 先绘制灰色背景圆（无论是否有图片，都需要背景）
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AVATAR_BG_COLOR }
        canvas.drawCircle(cx, cy, radius, circlePaint)

        if (avatarBitmap != null) {
            // 有实际头像 → 通过 Path 裁剪为圆形再绘制 Bitmap
            val srcRect = Rect(0, 0, avatarBitmap.width, avatarBitmap.height)
            val dstRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            val clipPath = Path().apply {
                addCircle(cx, cy, radius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)           // 设置裁剪路径为圆形
            canvas.drawBitmap(avatarBitmap, srcRect, dstRect, null)
            canvas.restore()                     // 恢复裁剪状态
        } else {
            // 无实际头像，取姓名首字母绘制在灰色圆形中央
            val initial = if (name.isNotBlank()) name.first().toString() else ""
            if (initial.isNotBlank()) {
                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 48f
                    color = Color.WHITE
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
                // cy + 16f 为视觉居中偏移量（文字基线偏下）
                canvas.drawText(initial, cx, cy + 16f, textPaint)
            }
        }

        // 灰色细边框（描边圆形）
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.parseColor("#CCCCCC")
        }
        canvas.drawCircle(cx, cy, radius, borderPaint)
    }

    /**
     * 从 URI 加载头像 Bitmap
     *
     * 优先通过 ContentResolver 打开 InputStream 解码，
     * 如果失败（URI 可能是文件路径而非 content://），则尝试直接读取文件。
     *
     * @param uriString 头像 URI 字符串（content:// 或 file:// 或绝对路径）
     * @return 解码后的 Bitmap，加载失败返回 null
     */
    private fun loadAvatarBitmap(context: Context, uriString: String): Bitmap? {
        if (uriString.isBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888  // 保持 32 位色彩
                    inSampleSize = 2                              // 缩小采样以节省内存
                })
            }
        } catch (e: Exception) {
            // ContentResolver 失败时尝试直接文件路径读取
            try {
                val file = File(Uri.parse(uriString).path ?: "")
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * 绘制一行带图标的联系信息
     *
     * 格式：[图标（Unicode字符）] [文本]
     * 例如：☎ 13800138000
     *
     * @param icon Unicode 图标字符（如 ☎ ✉ ⌂）
     * @param text 信息文本内容
     * @param iconLeft 图标起始 X 坐标
     * @param textLeft 文本起始 X 坐标（图标右侧留空后）
     * @param top 当前行顶部 Y 坐标
     * @param lineColor 该行文字（含图标）颜色
     * @return 下一行的起始 Y 坐标（用于连续绘制）
     */
    private fun drawInfoLine(
        canvas: Canvas,
        icon: String,
        text: String,
        iconLeft: Float,
        textLeft: Float,
        top: Float,
        lineColor: Int
    ): Float {
        if (text.isBlank()) return top  // 空行跳过，不占用垂直空间

        val iconPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = ICON_SIZE
            this.color = lineColor
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = FONT_SIZE_INFO
            this.color = lineColor
        }

        canvas.drawText(icon, iconLeft, top + FONT_SIZE_INFO, iconPaint)
        canvas.drawText(text, textLeft, top + FONT_SIZE_INFO, textPaint)

        // 返回下一行 Y 坐标（含行间距）
        return top + FONT_SIZE_INFO + LINE_SPACING * 2
    }

    /**
     * 创建多行文字布局（StaticLayout）
     *
     * @param text 文本内容
     * @param width 最大宽度（像素），超出自动换行
     * @param fontSize 字号的像素值
     * @param textColor 文字颜色
     * @return StaticLayout 对象，可通过 .height 获取总高度，通过 .draw() 绘制
     */
    @Suppress("DEPRECATION")
    private fun makeStaticLayout(text: String, width: Int, fontSize: Float, textColor: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSize
            this.color = textColor
        }
        return StaticLayout(
            text, 0, text.length, paint, width,
            Layout.Alignment.ALIGN_NORMAL,  // 左对齐
            1.0f,                            // 行间距倍数
            4f,                              // 段间距
            false                            // 是否包含 padding
        )
    }

    /**
     * 统计非空字符串的数量
     * 用于动态计算联系信息占据的行数
     *
     * @param values 可变数量的字符串参数
     * @return 非空字符串的个数
     */
    private fun countNonBlank(vararg values: String): Int =
        values.count { it.isNotBlank() }
}