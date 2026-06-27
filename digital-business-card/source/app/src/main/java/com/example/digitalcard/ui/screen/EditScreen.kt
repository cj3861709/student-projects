// 名片编辑页面，支持输入姓名/职位/电话/邮箱等字段，使用相册或拍照选取头像
package com.example.digitalcard.ui.screen

import android.content.Context                           // Android上下文
import android.net.Uri                                  // 统一资源标识符（图片访问）
import androidx.activity.compose.rememberLauncherForActivityResult  // 记住Activity结果启动器
import androidx.activity.result.contract.ActivityResultContracts    // 预定义的Activity结果合约
import androidx.compose.foundation.background           // 背景绘制
import androidx.compose.foundation.layout.*             // Compose布局组件（Column, Row, Spacer等）
import androidx.compose.foundation.rememberScrollState  // 滚动状态跟踪
import androidx.compose.foundation.shape.CircleShape    // 圆形裁剪形状
import androidx.compose.foundation.shape.RoundedCornerShape  // 圆角形状
import androidx.compose.foundation.verticalScroll       // 垂直滚动修饰符
import androidx.compose.material.icons.Icons            // Material图标
import androidx.compose.material.icons.filled.*         // 填充风格图标（Person, Phone等）
import androidx.compose.material3.ExperimentalMaterial3Api  // Material3实验性API标记
import androidx.compose.material3.*                     // Material3组件（TopAppBar, Scaffold, TextField等）
import androidx.compose.runtime.*                       // Compose运行时（mutableStateOf, remember等）
import androidx.compose.ui.Alignment                    // 对齐方式
import androidx.compose.ui.Modifier                     // UI修饰符
import androidx.compose.ui.draw.clip                    // 裁剪修饰符
import androidx.compose.ui.graphics.Color               // 颜色
import androidx.compose.ui.layout.ContentScale         // 图片内容缩放模式
import androidx.compose.ui.platform.LocalContext        // 获取当前Compose树的Context
import androidx.compose.ui.text.font.FontWeight        // 字体粗细
import androidx.compose.ui.unit.dp                     // dp单位
import androidx.compose.ui.unit.sp                     // sp字体单位
import coil.compose.AsyncImage                         // Coil异步图片加载组件
import coil.compose.AsyncImagePainter                  // Coil图片加载器状态
import coil.request.ImageRequest                       // Coil图片请求
import com.example.digitalcard.viewmodel.CardViewModel // 卡片ViewModel
import java.io.File                                    // 文件操作
import java.io.FileOutputStream                        // 文件输出流

/**
 * 名片编辑页面
 *
 * 功能：
 * 1. 提供头像选择（相册/拍照），图片自动复制到应用私有目录确保持久化
 * 2. 提供所有名片字段的编辑输入框（姓名、职位、电话、邮箱、公司、个人简介）
 * 3. 顶部TopAppBar提供"取消"和"保存"操作
 *
 * 头像处理流程：
 * - 无论是相册选择还是拍照，最终都复制到 files/avatars/ 目录
 * - 使用 file:// URI 指向私有目录文件，无需运行时权限即可读写
 * - 拍照使用 FileProvider 生成临时 URI，拍照完成后再复制到持久目录
 *
 * @param viewModel 卡片业务逻辑ViewModel，提供所有数据和更新方法
 * @param onSave 保存按钮回调（由CardApp传入，调用viewModel.saveCard()）
 * @param onCancel 取消按钮回调（由CardApp传入，调用viewModel.cancelEditing()）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    viewModel: CardViewModel,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    // 获取Compose树中的Android Context
    val context = LocalContext.current
    // 滚动状态，使页面可滚动适应小屏幕
    val scrollState = rememberScrollState()

    // 拍照后的临时 URI（拍照完成前需要先创建目标文件，由FileProvider管理）
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    // 头像是否正在加载（Coil异步加载时显示进度指示器）
    var isAvatarLoading by remember { mutableStateOf(false) }

    // ==================== 头像选择器（相册） ====================
    // rememberLauncherForActivityResult 是 Compose 中启动 Activity 的标准方式
    // ActivityResultContracts.GetContent 调用系统相册选择器
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 获取持久化权限：使 Uri 在应用重启后仍然可读
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // 有些图库选择器返回的 URI 不支持持久化权限，忽略
            }
            // 将原始 URI 指向的图片复制到应用私有目录，确保持久保存
            val persistentUri = copyToPrivateDir(context, it)
            viewModel.updateAvatar(persistentUri)
        }
    }

    // ==================== 头像选择器（拍照） ====================
    // ActivityResultContracts.TakePicture 调用系统相机
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            // 拍照成功后，将照片从 cache 临时目录复制到 files/avatars/ 私有目录
            val persistentUri = copyFromTempUri(context, tempCameraUri!!)
            viewModel.updateAvatar(persistentUri)
        }
    }

    /**
     * 创建拍照用的临时文件 URI
     *
     * 系统相机要求传入一个可写入的文件 URI，拍照完成后照片会写入该文件。
     * 使用 FileProvider 将 cache 目录下的临时文件暴露为 content:// URI。
     *
     * @return content:// 格式的 URI（通过 FileProvider 授权）
     */
    fun createTempImageUri(): Uri {
        val dir = File(context.cacheDir, "temp_camera")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",  // 对应 AndroidManifest 中的 FileProvider authority
            file
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑名片") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    TextButton(onClick = onSave) {
                        Text("保存", fontWeight = FontWeight.Bold)
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ========== 头像区域 ==========
            Text(
                text = "头像",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    if (viewModel.cardInfo.avatarUri.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(viewModel.cardInfo.avatarUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onState = { state ->
                                isAvatarLoading = state is AsyncImagePainter.State.Loading
                            }
                        )
                        // 加载中显示进度指示器
                        if (isAvatarLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 从相册选择
                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") }
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("相册")
                }
                // 拍照
                OutlinedButton(
                    onClick = {
                        tempCameraUri = createTempImageUri()
                        tempCameraUri?.let { uri ->
                            cameraLauncher.launch(uri)
                        }
                    }
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拍照")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ========== 信息编辑区域 ==========
            EditField(
                label = "姓名",
                value = viewModel.cardInfo.name,
                onValueChange = { viewModel.updateName(it) },
                placeholder = "请输入姓名",
                icon = Icons.Filled.Person
            )

            EditField(
                label = "职位",
                value = viewModel.cardInfo.position,
                onValueChange = { viewModel.updatePosition(it) },
                placeholder = "请输入职位",
                icon = Icons.Filled.Badge
            )

            EditField(
                label = "电话",
                value = viewModel.cardInfo.phone,
                onValueChange = { viewModel.updatePhone(it) },
                placeholder = "请输入电话号码",
                icon = Icons.Filled.Phone,
                isPhone = true
            )

            EditField(
                label = "邮箱",
                value = viewModel.cardInfo.email,
                onValueChange = { viewModel.updateEmail(it) },
                placeholder = "请输入邮箱地址",
                icon = Icons.Filled.Email,
                isEmail = true
            )

            EditField(
                label = "公司/学校",
                value = viewModel.cardInfo.company,
                onValueChange = { viewModel.updateCompany(it) },
                placeholder = "请输入公司或学校",
                icon = Icons.Filled.Business
            )

            // 个人简介（多行）
            Text(
                text = "个人简介",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
            OutlinedTextField(
                value = viewModel.cardInfo.bio,
                onValueChange = { viewModel.updateBio(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("请输入个人简介") },
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 将 URI 指向的图片复制到应用私有目录 files/avatars/
 * 返回新的 content:// URI（指向私有目录文件）
 */
private fun copyToPrivateDir(context: Context, sourceUri: Uri): String {
    return try {
        val avatarDir = File(context.filesDir, "avatars")
        if (!avatarDir.exists()) avatarDir.mkdirs()

        val targetFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.jpg")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        // 返回 file:// URI（指向应用私有目录，不需要权限）
        targetFile.toURI().toString()
    } catch (e: Exception) {
        e.printStackTrace()
        // 如果复制失败，回退到原始 URI
        sourceUri.toString()
    }
}

/**
 * 从临时拍照缓存目录复制图片到应用私有目录
 */
private fun copyFromTempUri(context: Context, tempUri: Uri): String {
    return try {
        val avatarDir = File(context.filesDir, "avatars")
        if (!avatarDir.exists()) avatarDir.mkdirs()

        val targetFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.jpg")

        context.contentResolver.openInputStream(tempUri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        targetFile.toURI().toString()
    } catch (e: Exception) {
        e.printStackTrace()
        tempUri.toString()
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPhone: Boolean = false,
    isEmail: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}