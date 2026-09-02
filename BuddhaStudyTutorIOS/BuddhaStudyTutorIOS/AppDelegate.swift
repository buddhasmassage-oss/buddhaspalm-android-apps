import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

extension Notification.Name {
    static let tutorFCMTokenUpdated = Notification.Name("TutorFCMTokenUpdated")
    static let tutorOpenURL = Notification.Name("TutorOpenURL")
}

@main
final class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        configureFirebaseIfAvailable()

        let center = UNUserNotificationCenter.current()
        center.delegate = self
        center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
            }
        }

        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = TutorViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }

    private func configureFirebaseIfAvailable() {
        guard FirebaseApp.app() == nil else { return }
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            print("BuddhaStudy Tutor: GoogleService-Info.plist not bundled yet; FCM is disabled until it is added.")
            return
        }
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        guard FirebaseApp.app() != nil else { return }
        Messaging.messaging().apnsToken = deviceToken
        Messaging.messaging().token { token, error in
            if let token = token {
                NotificationCenter.default.post(name: .tutorFCMTokenUpdated, object: token)
            } else if let error = error {
                print("BuddhaStudy Tutor FCM token error: \(error)")
            }
        }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("BuddhaStudy Tutor APNs registration error: \(error)")
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken, !fcmToken.isEmpty else { return }
        NotificationCenter.default.post(name: .tutorFCMTokenUpdated, object: fcmToken)
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .badge, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        let candidates = ["click_url", "url", "link"]
        for key in candidates {
            if let value = info[key] as? String,
               let url = URL(string: value),
               url.host?.lowercased() == "tutor.buddhaspalm.net" {
                NotificationCenter.default.post(name: .tutorOpenURL, object: url)
                break
            }
        }
        completionHandler()
    }
}
