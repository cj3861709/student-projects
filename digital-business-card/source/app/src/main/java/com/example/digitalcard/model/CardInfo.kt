// 名片信息数据模型，包含姓名/头像/职位/电话等字段，提供vCard格式导出（支持中文明文UTF-8编码）
package com.example.digitalcard.model

/**
 * 名片信息数据模型（不可变数据类）
 *
 * 使用 Kotlin data class 实现：
 * - 自动生成 equals()/hashCode()/toString()/copy() 等实用方法
 * - copy() 方法在 ViewModel 中用于"修改即新建"的不可变模式
 * - 所有字段默认值为空字符串，保证 DataStore 反序列化时不会出现 null
 *
 * @property name 名片上的姓名
 * @property avatarUri 头像图片的URI（content:// 或 file:// 路径）
 * @property position 职位/职称
 * @property phone 手机号码（用于二维码扫码后直接拨号）
 * @property email 电子邮箱
 * @property company 公司/组织名称
 * @property bio 个人简介/签名（显示在名片底部）
 * @property templateIndex 选中的模板索引（0=简约白, 1=科技蓝, 2=学术灰）
 */
data class CardInfo(
    val name: String = "",
    val avatarUri: String = "",
    val position: String = "",
    val phone: String = "",
    val email: String = "",
    val company: String = "",
    val bio: String = "",
    val templateIndex: Int = 0  // 0=简约白, 1=科技蓝, 2=学术灰
) {
    /**
     * 生成 vCard 格式字符串，用于二维码编码
     *
     * vCard 是电子名片的标准格式（RFC 6350），主要用于：
     * - 扫码后自动添加联系人到手机通讯录
     * - 兼容微信、支付宝、系统通讯录扫码功能
     *
     * 中文兼容方案说明：
     * - 之前版本使用 QUOTED-PRINTABLE 编码中文，但微信/支付宝等国产App
     *   对 QP 解码支持不完善，导致中文显示为乱码（=E5=AD=A6这类字节串）
     * - 当前方案直接输出 UTF-8 明文中文，并给每个中文字段加上
     *   CHARSET=UTF-8 参数，告知解析器按 UTF-8 处理
     * - 同时 QrCodeGenerator 必须设置 CHARACTER_SET=UTF-8 hint，
     *   让 ZXing 在 QR 码中包含 ECI 编码标记，
     *   否则扫码器默认使用 ISO-8859-1 解码 UTF-8 字节 → ??? 乱码
     * - QR码本身以 UTF-8 编码文本，扫码器能正确读取中文字符
     *
     * 使用 \r\n 换行符（CRLF），严格遵循 vCard RFC 6350 规范
     *
     * @return 符合 vCard 3.0 标准的文本字符串
     */
    fun toVCard(): String = buildString {
        // \r\n 是 vCard 标准要求的行结束符（CRLF）
        val nl = "\r\n"
        append("BEGIN:VCARD$nl")
        append("VERSION:3.0$nl")
        // 电话、邮箱纯 ASCII，直接输出
        if (phone.isNotBlank()) append("TEL;TYPE=CELL:$phone$nl")
        if (email.isNotBlank()) append("EMAIL:$email$nl")
        // 中文字段直接输出 UTF-8 明文，加 CHARSET=UTF-8 声明
        // 不加 QP 编码，因为微信/支付宝等 App 不支持正确解码 QP
        if (name.isNotBlank()) append("FN;CHARSET=UTF-8:$name$nl")
        if (company.isNotBlank()) append("ORG;CHARSET=UTF-8:$company$nl")
        if (position.isNotBlank()) append("TITLE;CHARSET=UTF-8:$position$nl")
        if (bio.isNotBlank()) append("NOTE;CHARSET=UTF-8:$bio$nl")
        append("END:VCARD$nl")
    }

    /**
     * 检查名片是否为空（无任何有效信息）
     *
     * 用于首次使用时空名片的判断场景
     *
     * @return true 表示所有字段均为空白
     */
    fun isEmpty(): Boolean = name.isBlank()
            && avatarUri.isBlank()
            && position.isBlank()
            && phone.isBlank()
            && email.isBlank()
            && company.isBlank()
            && bio.isBlank()
}
