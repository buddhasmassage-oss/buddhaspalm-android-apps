import UIKit
import WebKit

final class TutorViewController: UIViewController, WKNavigationDelegate, WKUIDelegate {
    private let allowedHost = "tutor.buddhaspalm.net"
    private var webView: WKWebView!
    private var progressView: UIProgressView!
    private var latestFCMToken: String?

    override var prefersStatusBarHidden: Bool { false }

    override func viewDidLoad() {
        super.viewDidLoad()

        edgesForExtendedLayout = []
        extendedLayoutIncludesOpaqueBars = false
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
        config.applicationNameForUserAgent = "BuddhaStudyTutorIOS/1.0.1"

        let webpagePreferences = WKWebpagePreferences()
        webpagePreferences.preferredContentMode = .mobile
        config.defaultWebpagePreferences = webpagePreferences

        let contentController = WKUserContentController()
        contentController.addUserScript(
            WKUserScript(
                source: nativeLayoutBootstrapScript(),
                injectionTime: .atDocumentStart,
                forMainFrameOnly: true
            )
        )
        config.userContentController = contentController

        webView = WKWebView(frame: .zero, configuration: config)
        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true
        webView.isOpaque = true

        // The web view itself is already inside the iOS safe area. Prevent
        // WebKit from adding a second automatic inset that can distort layout.
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.scrollView.contentInset = .zero
        webView.scrollView.scrollIndicatorInsets = .zero
        webView.scrollView.keyboardDismissMode = .interactive
        webView.scrollView.alwaysBounceHorizontal = false
        webView.scrollView.showsHorizontalScrollIndicator = false

        if #available(iOS 14.0, *) {
            webView.pageZoom = 1.0
        }

        webView.addObserver(
            self,
            forKeyPath: #keyPath(WKWebView.estimatedProgress),
            options: .new,
            context: nil
        )

        view.addSubview(webView)

        // Keep ALL edges inside the safe area so the Tutor logo, notification
        // bell, headers, bottom navigation and controls never sit under the
        // notch, Dynamic Island, status bar or home indicator.
        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
            webView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
    }

    private func buildProgressView() {
        progressView = UIProgressView(progressViewStyle: .bar)
        progressView.translatesAutoresizingMaskIntoConstraints = false
        progressView.progress = 0
        view.addSubview(progressView)

        NSLayoutConstraint.activate([
            progressView.topAnchor.constraint(equalTo: webView.topAnchor),
            progressView.leadingAnchor.constraint(equalTo: webView.leadingAnchor),
            progressView.trailingAnchor.constraint(equalTo: webView.trailingAnchor)
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

        var request = URLRequest(
            url: url,
            cachePolicy: .reloadRevalidatingCacheData,
            timeoutInterval: 30
        )
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        webView.load(request)
    }

    private func nativeLayoutBootstrapScript() -> String {
        return """
        (function () {
          window.BuddhaTutorNative = window.BuddhaTutorNative || {};
          window.BuddhaTutorNative.platform = 'ios';
          window.BuddhaTutorNative.standalone = true;
          window.BuddhaTutorNative.version = '1.0.1';

          function applyTutorIOSLayout() {
            try {
              var root = document.documentElement;
              if (root) {
                root.classList.add('bsp-ios-native');
                root.setAttribute('data-bsp-native', 'ios');
              }

              var viewport = document.querySelector('meta[name="viewport"]');
              if (!viewport) {
                viewport = document.createElement('meta');
                viewport.setAttribute('name', 'viewport');
                (document.head || root).appendChild(viewport);
              }
              viewport.setAttribute(
                'content',
                'width=device-width, initial-scale=1.0, viewport-fit=contain'
              );

              var style = document.getElementById('bsp-ios-native-layout');
              if (!style) {
                style = document.createElement('style');
                style.id = 'bsp-ios-native-layout';
                style.textContent = [
                  'html.bsp-ios-native{width:100%!important;max-width:100%!important;overflow-x:hidden!important;-webkit-text-size-adjust:100%!important;text-size-adjust:100%!important;}',
                  'html.bsp-ios-native body{width:100%!important;max-width:100%!important;min-height:100%!important;margin-left:auto!important;margin-right:auto!important;overflow-x:hidden!important;-webkit-text-size-adjust:100%!important;text-size-adjust:100%!important;}',
                  'html.bsp-ios-native *,html.bsp-ios-native *::before,html.bsp-ios-native *::after{box-sizing:border-box;}',
                  'html.bsp-ios-native .app-wrapper{width:100%!important;max-width:680px!important;margin-left:auto!important;margin-right:auto!important;}',
                  '@media (max-width:768px){html.bsp-ios-native input:not([type="checkbox"]):not([type="radio"]):not([type="range"]),html.bsp-ios-native textarea,html.bsp-ios-native select{font-size:16px!important;}}'
                ].join('');
                (document.head || root).appendChild(style);
              }
              return true;
            } catch (e) {
              return false;
            }
          }

          window.__bspApplyIOSNativeLayout = applyTutorIOSLayout;

          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', applyTutorIOSLayout, { once: true });
          } else {
            applyTutorIOSLayout();
          }
        })();
        """
    }

    private func reapplyNativeLayoutIfReady() {
        let js = """
        (function(){
          if (typeof window.__bspApplyIOSNativeLayout === 'function') {
            return window.__bspApplyIOSNativeLayout();
          }
          return false;
        })();
        """
        webView.evaluateJavaScript(js, completionHandler: nil)
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
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.1"
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
        if #available(iOS 14.0, *) {
            webView.pageZoom = 1.0
        }
        reapplyNativeLayoutIfReady()
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
            // Tutor iOS is intentionally isolated to tutor.buddhaspalm.net.
            // Any other website opens outside the Tutor app.
            if host == allowedHost {
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
