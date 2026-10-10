import SwiftUI
import UIKit
import Shared

/// O app no iPhone é só esta casca: a tela inteira é o mesmo app do Android,
/// escrito em Kotlin (o `MainViewController` do módulo `shared`).
@main
struct BBSolutionApp: App {
    var body: some Scene {
        WindowGroup {
            TelaCompose()
                // As margens do entalhe, da barra de baixo e do teclado são
                // tratadas pelas próprias telas, como no Android.
                .ignoresSafeArea()
        }
    }
}

struct TelaCompose: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
