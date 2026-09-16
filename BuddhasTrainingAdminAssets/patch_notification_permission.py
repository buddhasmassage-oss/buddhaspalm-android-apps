from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('Usage: patch_notification_permission.py <MainActivity.java>')

p = Path(sys.argv[1])
s = p.read_text(encoding='utf-8')

if 'import android.app.AlertDialog;' not in s:
    s = s.replace('import android.app.Activity;\n', 'import android.app.Activity;\nimport android.app.AlertDialog;\n')
if 'import android.provider.Settings;' not in s:
    s = s.replace('import android.os.Bundle;\n', 'import android.os.Bundle;\nimport android.provider.Settings;\n')

s = s.replace('BuddhasTrainingAdmin-Android/1.0.2', 'BuddhasTrainingAdmin-Android/1.0.3')

start_marker = '    private void requestNotificationPermission() {'
end_marker = '    private void loadFcmToken() {'
start = s.find(start_marker)
end = s.find(end_marker, start)
if start < 0 or end < 0:
    raise SystemExit('Notification permission method block not found')

replacement = '''    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;

        if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            new AlertDialog.Builder(this)
                    .setTitle("Enable Admin Notifications")
                    .setMessage("Allow notification permission so Buddhas Training Admin can alert you about new registrations, course enrollments, orders, chat messages and other Admin events.")
                    .setCancelable(false)
                    .setPositiveButton("Allow Notifications", (dialog, which) ->
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS))
                    .setNegativeButton("Not Now", null)
                    .show();
        } else {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NOTIFICATIONS) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            NotificationHelper.ensureChannel(this);
            loadFcmToken();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Admin Notifications Are Off")
                .setMessage("Notifications are needed for Admin alerts. You can enable them anytime in Android notification settings.")
                .setPositiveButton("Open Notification Settings", (dialog, which) -> openNotificationSettings())
                .setNegativeButton("Later", null)
                .show();
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception ignored) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

'''

s = s[:start] + replacement + s[end:]
p.write_text(s, encoding='utf-8')
print('Patched Android 13+ notification permission flow in', p)
