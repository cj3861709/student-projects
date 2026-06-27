// Compose名片模板内容组件，提供简约白/科技蓝/学术灰三种模板风格，渲染名片预览
package com.example.digitalcard.ui.template

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.digitalcard.model.CardInfo

// ============================================================
// 名片模板统一接口
// ============================================================

/**
 * 模板类型枚举
 */
enum class TemplateType(val label: String, val index: Int) {
    SIMPLE_WHITE("简约白", 0),
    TECH_BLUE("科技蓝", 1),
    ACADEMIC_GRAY("学术灰", 2)
}

/**
 * 根据索引获取模板
 */
@Composable
fun CardTemplateContent(
    cardInfo: CardInfo,
    qrBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    when (cardInfo.templateIndex) {
        0 -> SimpleWhiteTemplate(cardInfo, qrBitmap, modifier)
        1 -> TechBlueTemplate(cardInfo, qrBitmap, modifier)
        2 -> AcademicGrayTemplate(cardInfo, qrBitmap, modifier)
        else -> SimpleWhiteTemplate(cardInfo, qrBitmap, modifier)
    }
}

// ============================================================
// 1. 简约白模板
// ============================================================
@Composable
fun SimpleWhiteTemplate(
    cardInfo: CardInfo,
    qrBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 头像
            AvatarSection(cardInfo.avatarUri, cardInfo.name, Color(0xFFE0E0E0))

            Spacer(modifier = Modifier.height(16.dp))

            // 姓名
            if (cardInfo.name.isNotBlank()) {
                Text(
                    text = cardInfo.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 职位
            if (cardInfo.position.isNotBlank()) {
                Text(
                    text = cardInfo.position,
                    fontSize = 14.sp,
                    color = Color(0xFF757575)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 分割线
            HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // 联系信息
            InfoRow(Icons.Filled.Phone, cardInfo.phone, Color(0xFF616161))
            InfoRow(Icons.Filled.Email, cardInfo.email, Color(0xFF616161))
            InfoRow(Icons.Filled.Business, cardInfo.company, Color(0xFF616161))

            // 个人简介
            if (cardInfo.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = cardInfo.bio,
                    fontSize = 13.sp,
                    color = Color(0xFF9E9E9E),
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 二维码
            if (qrBitmap != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "二维码",
                    modifier = Modifier.size(100.dp)
                )
            }
        }
    }
}

// ============================================================
// 2. 科技蓝模板
// ============================================================
@Composable
fun TechBlueTemplate(
    cardInfo: CardInfo,
    qrBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A237E), Color(0xFF283593), Color(0xFF3949AB))
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box {
            // 渐变背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gradientBrush)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // 顶部装饰线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(3.dp)
                            .background(Color(0xFF00E5FF))
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // 头像 + 姓名区
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AvatarSection(cardInfo.avatarUri, cardInfo.name, Color(0xFF3949AB))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            if (cardInfo.name.isNotBlank()) {
                                Text(
                                    text = cardInfo.name,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            if (cardInfo.position.isNotBlank()) {
                                Text(
                                    text = cardInfo.position,
                                    fontSize = 14.sp,
                                    color = Color(0xFF90CAF9)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 信息卡片区域
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            TechInfoRow(Icons.Filled.Phone, cardInfo.phone)
                            TechInfoRow(Icons.Filled.Email, cardInfo.email)
                            TechInfoRow(Icons.Filled.Business, cardInfo.company)
                        }
                    }

                    if (cardInfo.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = cardInfo.bio,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 18.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 二维码浮在右下角
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(4.dp)
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "二维码",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ============================================================
// 3. 学术灰模板
// ============================================================
@Composable
fun AcademicGrayTemplate(
    cardInfo: CardInfo,
    qrBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // 顶部学院风装饰条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF37474F)))
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF546E7A)))
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF78909C)))
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 左侧：头像 + 姓名 + 职位
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(80.dp)
                ) {
                    AvatarSection(cardInfo.avatarUri, cardInfo.name, Color(0xFFBDBDBD))
                    Spacer(modifier = Modifier.height(8.dp))
                    if (cardInfo.name.isNotBlank()) {
                        Text(
                            text = cardInfo.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF37474F),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (cardInfo.position.isNotBlank()) {
                        Text(
                            text = cardInfo.position,
                            fontSize = 12.sp,
                            color = Color(0xFF78909C),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // 右侧：详细信息
                Column(modifier = Modifier.weight(1f)) {
                    if (cardInfo.company.isNotBlank()) {
                        Text(
                            text = cardInfo.company,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF455A64)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    AcademicInfoRow("电话", cardInfo.phone)
                    AcademicInfoRow("邮箱", cardInfo.email)

                    if (cardInfo.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = cardInfo.bio,
                                fontSize = 12.sp,
                                color = Color(0xFF607D8B),
                                lineHeight = 17.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 底部二维码
            if (qrBitmap != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "二维码",
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "扫码保存",
                            fontSize = 10.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// 共享组件
// ============================================================

@Composable
private fun AvatarSection(avatarUri: String, name: String, placeholderColor: Color) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUri.isNotBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // 显示姓名首字母作为占位
            val initial = if (name.isNotBlank()) name.first().toString() else ""
            if (initial.isNotBlank()) {
                Text(
                    text = initial,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String, color: Color) {
    if (text.isNotBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TechInfoRow(icon: ImageVector, text: String) {
    if (text.isNotBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF90CAF9)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AcademicInfoRow(label: String, text: String) {
    if (text.isNotBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Text(
                text = "$label:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF78909C),
                modifier = Modifier.width(40.dp)
            )
            Text(
                text = text,
                fontSize = 13.sp,
                color = Color(0xFF455A64),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
