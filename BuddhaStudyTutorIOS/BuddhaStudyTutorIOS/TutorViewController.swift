import UIKit
import WebKit

final class TutorViewController: UIViewController, WKNavigationDelegate, WKUIDelegate {
    private let allowedHost = "tutor.buddhaspalm.net"
    private var webView: WKWebView!
    private var progressView: UIProgressView!
    private var latestFCMToken: String?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        buildWebView()
        buildProgressView()
        observeNotifications()
        loadTutorHome()
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        if let webView {
            webView.removeObserver(self, forKeyPath: #keyPath(WKWebView.estimatedProgress))
        }
    }

    private func buildWebView() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        webView = WKWebView(frame: .zero, configuration: config)
        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.keyboardDismissMode = .interactive
        webView.addObserver(self, forKeyPath: #keyPath(WKWebView.estimatedProgress), options: .new, context: nil)

        view.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            webView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    private func buildProgressView() {
        progressView = UIProgressView(progressViewStyle: .bar)
        progressView.translatesAutoresizingMaskIntoConstraints = false
        progressView.progress = 0
        view.addSubview(progressView)
        NSLayoutConstraint.activate([
            progressView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            progressView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            progressView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
    }

    private func observeNotifications() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(fcmTokenUpdated(_:)),
            name: .tutorFCMTokenUpdated,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(openNotificationURL(_:)),
            name: .tutorOpenURL,
            object: nil
        )
    }

    private func loadTutorHome() {
        guard let url = URL(string: "https://tutor.buddhaspalm.net/") else { return }
        webView.load(URLRequest(url: url, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 30))
    }

    @objc private func fcmTokenUpdated(_ notification: Notification) {
        guard let token = notification.object as? String else { return }
        latestFCMToken = token
        injectFCMTokenIfReady()
    }

    @objc private func openNotificationURL(_ notification: Notification) {
        guard let url = notification.object as? URL else { return }
        guard url.host?.lowercased() == allowedHost else { return }
        webView.load(URLRequest(url: url))
    }

    private func injectFCMTokenIfReady() {
        guard let token = latestFCMToken, !token.isEmpty else { return }
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        let model = UIDevice.current.model
        let tokenJSON = jsonString(token)
        let modelJSON = jsonString(model)
        let versionJSON = jsonString(version)
        let js = """
        (function(){
          if (typeof window.bspRegisterNativeFcmToken === 'function') {
            window.bspRegisterNativeFcmToken(\(tokenJSON), \(modelJSON), \(versionJSON));
            return true;
          }
          return false;
        })();
        """
        webView.evaluateJavaScript(js, completionHandler: nil)
    }

    private func jsonString(_ value: String) -> String {
        let data = try? JSONSerialization.data(withJSONObject: [value], options: [])
        let array = data.flatMap { String(data: $0, encoding: .utf8) } ?? "[\"\"]"
        return String(array.dropFirst().dropLast())
    }

    override func observeValue(
        forKeyPath keyPath: String?,
        of object: Any?,
        change: [NSKeyValueChangeKey : Any]?,
        context: UnsafeMutableRawPointer?
    ) {
        guard keyPath == #keyPath(WKWebView.estimatedProgress) else {
            super.observeValue(forKeyPath: keyPath, of: object, change: change, context: context)
            return
        }
        progressView.progress = Float(webView.estimatedProgress)
        progressView.isHidden = webView.estimatedProgress >= 1.0
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        injectFCMTokenIfReady()
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel)
            return
        }

        let scheme = url.scheme?.lowercased() ?? ""
        let host = url.host?.lowercased()

        if scheme == "https" || scheme == "http" {
            if host == allowedHost || host?.hasSuffix(".buddhaspalm.net") == true {
                decisionHandler(.allow)
            } else {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
            }
            return
        }

        if ["mailto", "tel", "sms"].contains(scheme) {
            UIApplication.shared.open(url)
            decisionHandler(.cancel)
            return
        }

        decisionHandler(.allow)
    }

    @available(iOS 15.0, *)
    func webView(
        _ webView: WKWebView,
        requestMediaCapturePermissionFor origin: WKSecurityOrigin,
        initiatedByFrame frame: WKFrameInfo,
        type: WKMediaCaptureType,
        decisionHandler: @escaping (WKPermissionDecision) -> Void
    ) {
        let host = origin.host.lowercased()
        decisionHandler(host == allowedHost ? .grant : .prompt)
    }
}
