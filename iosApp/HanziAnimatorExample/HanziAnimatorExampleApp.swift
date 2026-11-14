import SwiftUI
import HanziSample

@main
struct HanziAnimatorExampleApp: App {
    var body: some Scene { WindowGroup { HanziContentView() } }
}

struct ComposeHanziView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

struct HanziContentView: View {
    @State private var didRunSmoke = false
    var body: some View {
        ComposeHanziView()
            .ignoresSafeArea(.keyboard)
            .onAppear {
                guard !didRunSmoke, ProcessInfo.processInfo.arguments.contains("--bundled-data-smoke") else { return }
                didRunSmoke = true
                runInstalledDataSmoke()
            }
    }

    private func runInstalledDataSmoke() {
        let required = ["strokes/all.hzb", "licenses/Arphic-Public-License.txt", "licenses/makemeahanzi-LGPL.txt", "licenses/makemeahanzi-GPL.txt", "licenses/makemeahanzi-COPYING.txt"]
        let missing = required.filter { !FileManager.default.fileExists(atPath: Bundle.main.bundlePath + "/" + $0) }
        guard missing.isEmpty else { writeSmokeResult("FAIL: Missing installed resources: " + missing.joined(separator: ", ")); return }
        MainViewControllerKt.runBundledDataSmokeTest { result in
            writeSmokeResult(result)
        }
    }

    private func writeSmokeResult(_ result: String) {
        print("HANZI_BUNDLED_DATA_SMOKE: \(result)")
        guard let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        do { try result.write(to: documents.appendingPathComponent("bundled-data-smoke.txt"), atomically: true, encoding: .utf8) }
        catch { print("HANZI_BUNDLED_DATA_SMOKE: cannot write result: \(error)") }
    }
}
