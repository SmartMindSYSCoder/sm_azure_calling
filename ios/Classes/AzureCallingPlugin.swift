import Flutter
import UIKit
import AzureCommunicationCommon
import AzureCommunicationUICalling

public class SwiftAzureCallingPlugin: NSObject, FlutterPlugin {
  private var channel: FlutterMethodChannel!

  public static func register(with registrar: FlutterPluginRegistrar) {
    let instance = SwiftAzureCallingPlugin()
    instance.channel = FlutterMethodChannel(name: "azure_calling", binaryMessenger: registrar.messenger())
    registrar.addMethodCallDelegate(instance, channel: instance.channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "getPlatformVersion":
      result("iOS " + UIDevice.current.systemVersion)

    case "startCall":
      guard let args = call.arguments as? [String: Any] else {
        result(FlutterError(code: "BAD_ARGS", message: "Expected map arguments", details: nil))
        return
      }
      let token = args["token"] as? String ?? ""
      let meetingLink = args["meetingLink"] as? String ?? ""
      let displayName = args["displayName"] as? String

      startCall(token: token, meetingLink: meetingLink, displayName: displayName, result: result)

    default:
      result(FlutterMethodNotImplemented)
    }
  }

  private func startCall(token: String, meetingLink: String, displayName: String?, result: @escaping FlutterResult) {
    // Create a token credential with proactive refresh (use your own refresher if needed)
    let refreshOptions = CommunicationTokenRefreshOptions(
      initialToken: token,
      refreshProactively: true
    ) { completion in
      // TODO: Replace with a real async refresh from your backend.
      // For now, reuse the same token:
      completion(.success(CommunicationAccessToken(token: token, expiresOn: nil)))
    }

    let credential = CommunicationTokenCredential(withOptions: refreshOptions)

    // Build locator for a Teams meeting link
    let locator: JoinLocator = .teamsMeeting(teamsLink: meetingLink) // group call would be .groupCall(groupId: UUID)

    // Build remote options (locator + credential + optional display name)
    let remote = RemoteOptions(for: locator, credential: credential, displayName: displayName)

    // Build and launch the composite
    let composite = CallComposite(withOptions: nil)

    // (optional) subscribe to errors/events
    composite.events.onError = { error in
      print("ACS UI Error: \(error.code) \(String(describing: error.error))")
    }

    // Get a UIViewController to present from
    guard let presenter = topMostViewController() else {
      result(FlutterError(code: "NO_UI", message: "Unable to find presenting UIViewController", details: nil))
      return
    }

    DispatchQueue.main.async {
      composite.launch(remoteOptions: remote, localOptions: nil)
      // CallComposite handles its own UI; nothing to return to Flutter right now
      result(nil)
    }
  }

  private func topMostViewController(base: UIViewController? = UIApplication.shared.connectedScenes
    .compactMap { $0 as? UIWindowScene }
    .flatMap { $0.windows }
    .first { $0.isKeyWindow }?.rootViewController) -> UIViewController? {
      if let nav = base as? UINavigationController {
        return topMostViewController(base: nav.visibleViewController)
      }
      if let tab = base as? UITabBarController, let selected = tab.selectedViewController {
        return topMostViewController(base: selected)
      }
      if let presented = base?.presentedViewController {
        return topMostViewController(base: presented)
      }
      return base
  }
}
