# Buddhas Ride Provider keeps the WebView bridge methods by name.
-keepclassmembers class com.buddhaspinas.buddhasride.provider.MainActivity$ProviderBridge {
    @android.webkit.JavascriptInterface <methods>;
}
