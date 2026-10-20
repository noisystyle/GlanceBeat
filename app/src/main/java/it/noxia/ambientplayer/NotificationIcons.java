package it.noxia.ambientplayer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.service.notification.NotificationListenerService.Ranking;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ephemeral icon metadata and cached drawable resources; no extras or message contents. Launch actions stay in memory only. */
final class NotificationIcons {
    record Snapshot(List<Drawable> icons, int appCount, List<PendingIntent> actions) {
        Snapshot(List<Drawable> icons,int appCount){this(icons,appCount,java.util.Collections.nCopies(icons.size(),null));}
        static final Snapshot EMPTY = new Snapshot(List.of(), 0);
    }
    private record Cached(String signature, Drawable icon) { }
    private final Map<String, Cached> cache = new HashMap<>();
    private Snapshot active = Snapshot.EMPTY, ambient = Snapshot.EMPTY;
    private String fingerprint = "";
    Snapshot snapshot(boolean dim) { return dim ? ambient : active; }
    boolean clear() {
        boolean changed = active.appCount() > 0 || ambient.appCount() > 0;
        active = ambient = Snapshot.EMPTY; cache.clear(); fingerprint = "";
        return changed;
    }
    boolean update(Context context, StatusBarNotification[] notifications, RankingMap rankings) {
        List<NotificationIconPolicy.Entry> entries = new ArrayList<>();
        Map<String, StatusBarNotification> sources = new HashMap<>();
        if (notifications != null) for (StatusBarNotification item : notifications) {
            Notification notification = item.getNotification();
            Ranking rank = new Ranking();
            boolean ranked = rankings != null && rankings.getRanking(item.getKey(), rank);
            boolean secret = notification.visibility == Notification.VISIBILITY_SECRET
                || ranked && rank.getLockscreenVisibilityOverride() == Notification.VISIBILITY_SECRET;
            boolean hidden = secret || item.getPackageName().equals(context.getPackageName())
                || AccessorySupport.hidesNotification(item.getPackageName())
                || NotificationIconPolicy.isSystemMediaIndicator(item.getPackageName(),notification.getChannelId())
                || Notification.CATEGORY_TRANSPORT.equals(notification.category)
                || ranked && (rank.isSuspended() || rank.getImportance() <= NotificationManager.IMPORTANCE_MIN)
                || notification.getSmallIcon() == null;
            boolean suppressed = ranked && (rank.getSuppressedVisualEffects()
                & NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT) != 0;
            entries.add(new NotificationIconPolicy.Entry(item.getPackageName()+"/"+item.getUid(),
                item.getKey(), item.getPostTime(), hidden, suppressed));
            sources.put(item.getKey(),item);
        }
        Map<String,Cached> retained = new HashMap<>();
        StringBuilder digest = new StringBuilder();
        Snapshot nextActive = build(context,NotificationIconPolicy.select(entries,false),sources,retained,digest);
        digest.append('|');
        Snapshot nextAmbient = build(context,NotificationIconPolicy.select(entries,true),sources,retained,digest);
        String next = digest.toString();
        if (fingerprint.equals(next)) return false;
        fingerprint = next; active = nextActive; ambient = nextAmbient;
        cache.clear(); cache.putAll(retained);
        return true;
    }
    private Snapshot build(Context context, List<NotificationIconPolicy.Entry> entries,
                           Map<String,StatusBarNotification> sources, Map<String,Cached> retained,
                           StringBuilder digest) {
        List<Drawable> icons = new ArrayList<>();
        List<PendingIntent> actions = new ArrayList<>();
        digest.append(entries.size()).append(':');
        for (NotificationIconPolicy.Entry entry : entries) {
            if (icons.size() == NotificationIconPolicy.MAX_ICONS) break;
            Icon small = sources.get(entry.notificationKey()).getNotification().getSmallIcon();
            // Resource signatures stay stable across text updates, avoiding repeated icon decoding.
            String signature = small.getType() == Icon.TYPE_RESOURCE
                ? small.getResPackage()+":"+small.getResId() : entry.notificationKey()+":"+entry.postedAt();
            Cached cached = retained.get(entry.appKey());
            if (cached == null || !cached.signature().equals(signature)) cached = cache.get(entry.appKey());
            if (cached == null || !cached.signature().equals(signature)) {
                Drawable drawable;
                try { drawable = small.loadDrawable(context); }
                catch (RuntimeException unavailable) { drawable = null; }
                if (drawable == null) continue;
                drawable = drawable.mutate(); drawable.setTint(Color.WHITE);
                cached = new Cached(signature,drawable);
            }
            retained.put(entry.appKey(),cached);
            icons.add(cached.icon());
            actions.add(sources.get(entry.notificationKey()).getNotification().contentIntent);
            digest.append(entry.appKey()).append('=').append(signature).append(':').append(entry.notificationKey()).append(':').append(entry.postedAt()).append(':').append(java.util.Objects.hashCode(actions.get(actions.size()-1))).append(';');
        }
        // Missing icon resources must not leave an unexplained empty slot.
        int count = icons.isEmpty() ? 0 : Math.max(icons.size(),entries.size());
        return new Snapshot(List.copyOf(icons),count,java.util.Collections.unmodifiableList(actions));
    }
}
