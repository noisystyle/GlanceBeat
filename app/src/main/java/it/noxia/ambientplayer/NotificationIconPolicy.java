package it.noxia.ambientplayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Only metadata is needed to select one icon per app/profile, without notification text. */
final class NotificationIconPolicy {
    static final int MAX_ICONS = 6;
    record Entry(String appKey, String notificationKey, long postedAt, boolean hidden,
                 boolean ambientSuppressed) { }
    // Samsung's technical media activity notification is not an app notification to show in the row.
    static boolean isSystemMediaIndicator(String packageName,String channelId) {
        return "com.android.systemui".equals(packageName) && "MediaOngoingActivity".equals(channelId);
    }
    static List<Entry> select(List<Entry> notifications, boolean ambient) {
        List<Entry> ordered = new ArrayList<>(notifications);
        ordered.sort(Comparator.comparingLong(Entry::postedAt).reversed().thenComparing(Entry::appKey));
        LinkedHashMap<String, Entry> apps = new LinkedHashMap<>();
        for (Entry entry : ordered) {
            if (entry.hidden() || ambient && entry.ambientSuppressed()) continue;
            apps.putIfAbsent(entry.appKey(), entry);
        }
        return List.copyOf(apps.values());
    }
    private NotificationIconPolicy() { }
}
