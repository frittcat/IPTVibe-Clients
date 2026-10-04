package tv.iptvibe.mobile;

public final class UpdateReceiver extends android.content.BroadcastReceiver {
    public void onReceive(android.content.Context context, android.content.Intent intent) {
        UpdateManager.result(context, intent);
    }
}
