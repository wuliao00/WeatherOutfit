package com.jianyi.outfit.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString
import platform.darwin.NSObject
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationSound

/**
 * 平台 API 形态探针 —— **用完即删**，不是产品代码。
 *
 * 背景：`UNMutableNotificationContent` 的 title / body / sound 在 Kotlin/Native 里
 * 报 "'val' cannot be reassigned"（子类把父类 readonly 属性重声明成 readwrite，
 * cinterop 似乎沿用了父类声明），而 KVC 的 `setValue(_:forKey:)` 又解析不到成员。
 * 本机是 Windows，iOS 不参与编译，猜一个试一个要一轮 CI。
 *
 * 所以把六种候选各写成一个独立函数一次推上去：Kotlin 编译器会**一次报出全部**
 * 不成立的写法，一轮就能问出哪种是真的。留下注释说明哪条通过了。
 */
@OptIn(ExperimentalForeignApi::class)
private object NotificationContentProbe {

    /** a) 基线：直接属性赋值（上一轮已知报 'val' cannot be reassigned） */
    fun probeA(content: UNMutableNotificationContent, text: String) {
        content.title = text
    }

    /** b) ObjC setter 是否被导成了函数 */
    fun probeB(content: UNMutableNotificationContent, text: String) {
        content.setTitle(text)
    }

    /** c) KVC 具名参数写法（上一轮报 "None of the following candidates is applicable"） */
    fun probeC(content: UNMutableNotificationContent, text: String) {
        content.setValue(text, forKey = "title")
    }

    /** d) KVC 位置参数写法 —— 排除"只是具名参数名不对"这种可能 */
    fun probeD(content: UNMutableNotificationContent, text: String) {
        content.setValue(text, "title")
    }

    /** e) 走 NSObject 的 performSelector:withObject: 手动调 setter */
    fun probeE(content: UNMutableNotificationContent, text: String) {
        content.performSelector(NSSelectorFromString("setTitle:"), withObject = text)
    }

    /** f) 显式按 NSObject 静态类型走 KVC —— 排除是子类视图遮蔽了 NSObject 成员 */
    fun probeF(content: NSObject, text: String) {
        content.setValue(text, forKey = "title")
    }

    /** g) 声音：class property 还是 class method？两个都写，看哪个报错 */
    fun probeG(): Any? {
        val asProperty = UNNotificationSound.defaultSound
        val asFunction = UNNotificationSound.defaultSound()
        return asProperty ?: asFunction
    }
}
