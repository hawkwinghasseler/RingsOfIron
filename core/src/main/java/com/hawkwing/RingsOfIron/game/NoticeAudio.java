package com.hawkwing.RingsOfIron.game;

import com.ringsofiron.audiospatial.AudioManager;
import com.ringsofiron.audiospatial.backend.AudioEventCatalog;

/**
 * A chime for important notices and a warning tone for danger, from audio-spatial's interface
 * sounds. A notice repeated while still on screen plays nothing again.
 */
public final class NoticeAudio {
    private NoticeAudio() {
    }

    public static void install(NoticeLog notices, AudioManager audio) {
        notices.posted.add((signal, n) -> {
            if (n.count > 1) return;
            if (n.kind == NoticeLog.Kind.IMPORTANT) audio.playUi(AudioEventCatalog.UI_NOTICE);
            else if (n.kind == NoticeLog.Kind.WARNING) audio.playUi(AudioEventCatalog.UI_WARNING);
        });
    }
}
