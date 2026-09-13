from pathlib import Path


def patch_project(project_name: str, is_admin: bool) -> None:
    project = Path(project_name)
    java = next((project / "app/src/main/java").rglob("MainActivity.java"))
    text = java.read_text()

    if "androidx.core.graphics.Insets" not in text:
        text = text.replace(
            "import android.widget.RelativeLayout;\n",
            "import android.widget.RelativeLayout;\n\n"
            "import androidx.core.graphics.Insets;\n"
            "import androidx.core.view.ViewCompat;\n"
            "import androidx.core.view.WindowCompat;\n"
            "import androidx.core.view.WindowInsetsCompat;\n"
            "import androidx.core.view.WindowInsetsControllerCompat;\n",
        )

    text = text.replace(
        "        getWindow().setStatusBarColor(Color.rgb(75, 23, 106));\n"
        "        getWindow().setNavigationBarColor(Color.rgb(50, 16, 69));\n",
        "        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);\n"
        "        getWindow().setStatusBarColor(Color.WHITE);\n"
        "        getWindow().setNavigationBarColor(Color.WHITE);\n"
        "        WindowInsetsControllerCompat bars = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());\n"
        "        bars.setAppearanceLightStatusBars(true);\n"
        "        bars.setAppearanceLightNavigationBars(true);\n",
    )

    if is_admin:
        old = (
            "        LinearLayout trackingBar = buildTrackingBar();\n"
            "        trackingBar.setId(View.generateViewId());\n\n"
            "        RelativeLayout.LayoutParams barParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, dp(52));\n"
            "        barParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);\n"
            "        root.addView(trackingBar, barParams);\n\n"
            "        RelativeLayout.LayoutParams webParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);\n"
            "        webParams.addRule(RelativeLayout.ABOVE, trackingBar.getId());\n"
            "        RelativeLayout.LayoutParams progParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, dp(3));\n"
            "        progParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);\n"
            "        progParams.addRule(RelativeLayout.ABOVE, trackingBar.getId());\n"
            "        root.addView(webView, webParams);\n"
            "        root.addView(progress, progParams);\n"
            "        setContentView(root);\n"
        )
        new = (
            "        RelativeLayout.LayoutParams webParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);\n"
            "        RelativeLayout.LayoutParams progParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, dp(3));\n"
            "        progParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);\n"
            "        root.addView(webView, webParams);\n"
            "        root.addView(progress, progParams);\n"
            "        setContentView(root);\n"
        )
        text = text.replace(old, new)

    marker = "        setContentView(root);\n\n        configureWebView();"
    safe = (
        "        setContentView(root);\n\n"
        "        // Android 15/API 35: keep the live PWA inside system-bar and cutout safe areas.\n"
        "        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {\n"
        "            Insets safeInsets = insets.getInsets(\n"
        "                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());\n"
        "            view.setPadding(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom);\n"
        "            return insets;\n"
        "        });\n"
        "        ViewCompat.requestApplyInsets(root);\n\n"
        "        configureWebView();"
    )
    if "ViewCompat.setOnApplyWindowInsetsListener(root" not in text:
        text = text.replace(marker, safe)

    settings_marker = "        s.setMediaPlaybackRequiresUserGesture(false);\n"
    settings = (
        "        s.setMediaPlaybackRequiresUserGesture(false);\n"
        "        s.setUseWideViewPort(true);\n"
        "        s.setLoadWithOverviewMode(false);\n"
        "        s.setTextZoom(100);\n"
        "        s.setDefaultFontSize(16);\n"
        "        s.setBuiltInZoomControls(false);\n"
        "        s.setDisplayZoomControls(false);\n"
        "        s.setSupportZoom(false);\n"
    )
    if "s.setTextZoom(100);" not in text:
        text = text.replace(settings_marker, settings)

    java.write_text(text)
    print(f"Patched {java}")


patch_project("BuddhasStaffNewAdmin", True)
patch_project("BuddhasStaffNewTherapist", False)
