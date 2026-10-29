import SwiftUI
import UIKit
import UserNotifications
import Shared

/// O app no iPhone é só esta casca: a tela inteira é o mesmo app do Android,
/// escrito em Kotlin (o `MainViewController` do módulo `shared`).
@main
struct BBSolutionApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

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

/// Os avisos (push) de OS: a Apple só conversa com o `AppDelegate`, então é
/// aqui que se pede a permissão, se recebe o token e se sabe que o técnico
/// tocou no aviso. Tudo segue para o Kotlin pelo `AvisosIos`.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        // Só depois do login. Quem já aceitou não vê a pergunta de novo: o iOS
        // responde na hora, e o token é renovado a cada abertura.
        AvisosIos.shared.aoEntrar {
            UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { aceitou, _ in
                guard aceitou else { return }
                DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
            }
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        AvisosIos.shared.definirToken(token: hex)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // Sem token não há aviso; a lista de OS continua chegando ao abrir o app.
        print("Aviso (push) indisponível: \(error.localizedDescription)")
    }

    /// Aviso com o app aberto: mostra mesmo assim, e a lista já atualiza.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        AvisosIos.shared.chegouComAppAberto()
        completionHandler([.banner, .list, .sound])
    }

    /// Tocou no aviso: abre a OS.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        AvisosIos.shared.abrirOs(id: response.notification.request.content.userInfo["osId"] as? String)
        completionHandler()
    }
}
