package it.noxia.ambientplayer;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationIconPolicyTest {
    private NotificationIconPolicy.Entry entry(String app,String key,long time,boolean hidden,boolean dnd) {
        return new NotificationIconPolicy.Entry(app,key,time,hidden,dnd);
    }
    @Test public void filtersOnlySamsungTechnicalMediaIndicator() {
        assertTrue(NotificationIconPolicy.isSystemMediaIndicator("com.android.systemui","MediaOngoingActivity"));
        assertFalse(NotificationIconPolicy.isSystemMediaIndicator("com.android.systemui","ALERTS"));
        assertFalse(NotificationIconPolicy.isSystemMediaIndicator("com.whatsapp","MediaOngoingActivity"));
        assertFalse(NotificationIconPolicy.isSystemMediaIndicator("com.android.systemui",null));
    }
    @Test public void severalMessagesUseOneRecentIconPerApp() {
        var result=NotificationIconPolicy.select(List.of(entry("chat/0","old",1,false,false),
            entry("mail/0","mail",2,false,false),entry("chat/0","new",3,false,false)),false);
        assertEquals(List.of("new","mail"),result.stream().map(NotificationIconPolicy.Entry::notificationKey).toList());
    }
    @Test public void hiddenNotificationCannotReplaceVisibleNotificationFromSameApp() {
        var result=NotificationIconPolicy.select(List.of(entry("chat/0","visible",1,false,false),
            entry("chat/0","secret",2,true,false),entry("private/0","hidden",3,true,false)),true);
        assertEquals(1,result.size());assertEquals("visible",result.get(0).notificationKey());
    }
    @Test public void ambientDndIsRespectedWithoutHidingActiveIcons() {
        var notifications=List.of(entry("chat/0","quiet",2,false,true),entry("chat/0","allowed",1,false,false),
            entry("mail/0","suppressed",3,false,true));
        assertEquals(2,NotificationIconPolicy.select(notifications,false).size());
        var ambient=NotificationIconPolicy.select(notifications,true);
        assertEquals(1,ambient.size());assertEquals("allowed",ambient.get(0).notificationKey());
    }
    @Test public void personalAndWorkProfilesRemainDistinct() {
        assertEquals(2,NotificationIconPolicy.select(List.of(entry("chat/0","personal",1,false,false),
            entry("chat/10","work",2,false,false)),true).size());
    }
    @Test public void emptyAndRemovedNotificationsLeaveNoRow() {
        assertTrue(NotificationIconPolicy.select(List.of(),true).isEmpty());
        assertTrue(NotificationIconPolicy.select(List.of(entry("chat/0","hidden",1,true,false)),false).isEmpty());
    }
}
