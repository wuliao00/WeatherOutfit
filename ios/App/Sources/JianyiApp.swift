import SwiftUI
import Shared

// 唯一入口：把 Kotlin 侧的 ComposeUIViewController 当 rootViewController。
//
// ⚠ 这份 Swift/工程文件在本仓库里**没有被编译过一次**：开发机是 Windows，
// 没有 macOS，CI 也只跑到 :shared 的 iosArm64 编译（那已经覆盖了全部 Kotlin 侧代码）。
// 也就是说：Kotlin 侧是编译验证过的，这个壳是按 KMP 官方向导的标准形状写的，
// 第一次在 Mac 上跑可能要调 build setting —— 按现实对待，别当成已完成品。
@main
struct JianyiApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)   // 边到边：风景背景要铺到状态栏与底部手势条下面
        }
    }
}

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
