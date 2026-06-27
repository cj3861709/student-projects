// 名片预览页面，展示渲染后的名片及二维码，支持保存图片到相册和一键分享
package com.example.digitalcard.ui.screen

import android.content.Context                           // Android上下文
import android.content.Intent                            // Android Intent（启动分享、更新相册）
import android.graphics.Bitmap                           // 位图（二维码）
import android.net.Uri                                   // 统一资源标识符
import android.os.Environment                            // 获取系统公共目录路径
import android.widget.Toast                              // 简短提示消息
import androidx.compose.foundation.layout.*              // Compose布局（Column, Row, Spacer等）
import androidx.compose.foundation.rememberScrollState   // 滚动状态跟踪
import androidx.compose.foundation.shape.RoundedCornerShape  // 圆角形状
import androidx.compose.foundation.verticalScroll         // 垂直滚动修饰符
import androidx.compose.material.icons.Icons              // Material图标
import androidx.compose.material.icons.filled.*           // 填充图标（Edit, Save, Share, Check等）
import androidx.compose.material3.ExperimentalMaterial3Api  // Material3实验性API
import androidx.compose.material3.*                       // Material3组件（Scaffold, FilterChip, Card, FilledButton等）
import androidx.compose.runtime.*                         // Compose运行时（rememberCoroutineScope等）
import androidx.compose.ui.Alignment                      // 对齐方式
import androidx.compose.ui.Modifier                       // UI修饰符
import androidx.compose.ui.graphics.Color               // 颜色
import androidx.compose.ui.platform.LocalContext          // 获取Compose树中的Context
import androidx.compose.ui.text.font.FontWeight          // 字体粗细
import androidx.compose.ui.unit.dp                       // dp单位
import androidx.compose.ui.unit.sp                       // sp单位
import androidx.core.content.FileProvider                // AndroidX FileProvider（安全地暴露文件URI）
import com.example.digitalcard.model.CardInfo             // 名片数据模型
import com.example.digitalcard.ui.template.CardTemplateContent  // 名片渲染Composable
import com.example.digitalcard.ui.template.CardTemplateRenderer // Canvas渲染工具（后台Bitmap生成）
import com.example.digitalcard.ui.template.TemplateType  // 模板枚举（简约白/科技蓝/学术灰）
import com.example.digitalcard.viewmodel.CardViewModel    // 卡片ViewModel
import kotlinx.coroutines.Dispatchers                    // 协程调度器（IO/主线程）
import kotlinx.coroutines.launch                         // 启动协程
import kotlinx.coroutines.withContext                    // 切换协程上下文
import java.io.File                                      // 文件操作（缓存目录写入）
import java.io.FileOutputStream                          // 文件输出流

/**
 * 名片预览页面
 *
 * 功能：
 * 1. 模板选择器：三个FilterChip对应三种模板风格（简约白/科技蓝/学术灰）
 * 2. 名片预览：调用 CardTemplateContent 渲染当前模板和二维码
 * 3. "保存图片"按钮：将名片渲染为Bitmap，保存到系统相册（适配Android 10+ MediaStore）
 * 4. "分享名片"按钮：生成Bitmap后通过Intent分享到微信/QQ等社交应用
 *
 * 渲染策略：
 * - UI部分的实时预览使用 CardTemplateContent（Composable，响应式）
 * - 保存/分享时使用 CardTemplateRenderer（Canvas绘制，后台线程执行）
 *   避免在主线程进行Bitmap操作导致UI卡顿
 *
 * @param viewModel 卡片业务逻辑ViewModel
 * @param onEdit 编辑按钮回调（切换回编辑页）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    viewModel: CardViewModel,
    onEdit: () -> Unit
) {
    // Compose树中的Android Context
    val context = LocalContext.current
    // 协程作用域，用于在按钮点击时启动协程执行异步操作（保存/分享）
    val scope = rememberCoroutineScope()

    // 从ViewModel中获取当前状态（Compose自动跟踪，数据变化时重组）
    val cardInfo = viewModel.cardInfo           // 名片数据
    val qrBitmap = viewModel.qrBitmap           // 二维码Bitmap（由ViewModel管理生成）
    val selectedIndex = viewModel.selectedTemplateIndex  // 当前模板索引
    // 滚动状态（页面内容可滚动）
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数字名片") },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ========== 模板选择器 ==========
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "选择模板风格",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TemplateType.entries.forEach { template ->
                            val isSelected = selectedIndex == template.index
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.switchTemplate(template.index) },
                                label = { Text(template.label, fontSize = 13.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ========== 名片预览 ==========
            CardTemplateContent(
                cardInfo = cardInfo,
                qrBitmap = qrBitmap,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ========== 操作按钮 ==========
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 保存到相册
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            saveCardToGallery(context, cardInfo, qrBitmap)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("保存图片", fontSize = 14.sp)
                }

                // 分享
                Button(
                    onClick = {
                        scope.launch {
                            shareCard(context, cardInfo, qrBitmap)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("分享名片", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ========== 提示信息 ==========
            if (cardInfo.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "点击右上角编辑按钮，创建您的第一张数字名片",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ============================================================
// 辅助函数
// ============================================================

/**
 * 渲染名片为 Bitmap 并保存到相册
 * 使用 Canvas 渲染（CardTemplateRenderer），可在后台线程执行，避免阻塞主线程
 */
private suspend fun saveCardToGallery(context: Context, cardInfo: CardInfo, qrBitmap: Bitmap?) {
    try {
        // 使用 Canvas 渲染，在 IO 线程执行，不阻塞主线程
        val bitmap = withContext(Dispatchers.IO) {
            CardTemplateRenderer.renderCardToBitmap(context, cardInfo, qrBitmap, 1080)
        }
        if (bitmap == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "生成图片失败", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val saved = withContext(Dispatchers.IO) {
            saveBitmapToGallery(context, bitmap)
        }

        withContext(Dispatchers.Main) {
            if (saved) {
                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "保存失败，请检查权限", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 分享名片
 * 使用 Canvas 渲染（CardTemplateRenderer），可在后台线程执行
 */
private suspend fun shareCard(context: Context, cardInfo: CardInfo, qrBitmap: Bitmap?) {
    try {
        // 使用 Canvas 渲染，在 IO 线程执行
        val bitmap = withContext(Dispatchers.IO) {
            CardTemplateRenderer.renderCardToBitmap(context, cardInfo, qrBitmap, 1080)
        }
        if (bitmap == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "生成图片失败", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 保存到缓存目录
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "card_share_${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            f
        }

        // 通过 FileProvider 获取 URI
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        // 启动分享 Intent
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        withContext(Dispatchers.Main) {
            context.startActivity(
                Intent.createChooser(shareIntent, "分享数字名片")
            )
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 保存 Bitmap 到系统相册
 */
private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val filename = "DigitalCard_${System.currentTimeMillis()}.png"
        // Android 10+ 使用 MediaStore
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/DigitalCard"
                )
            }
            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } != null
        } else {
            @Suppress("DEPRECATION")
            // Android 9 及以下
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "DigitalCard"
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            // 通知相册更新
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(file)
            context.sendBroadcast(intent)
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}