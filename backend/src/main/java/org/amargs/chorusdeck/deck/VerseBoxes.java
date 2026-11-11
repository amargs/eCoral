package org.amargs.chorusdeck.deck;

import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.liturgy.Lectionary;

import java.util.ArrayList;
import java.util.List; /** Text boxes of one tempo or one Sunday, in the order they are stored. */
public final class VerseBoxes {

    private VerseBoxes() {
    }

    public static List<String> of(List<String> verses, List<String> marks, String mark) {
        if (verses == null || verses.isEmpty() || marks == null || mark == null) {
            return List.of();
        }
        List<String> kept = new ArrayList<>();
        int limit = Math.min(verses.size(), marks.size());
        for (int i = 0; i < limit; i++) {
            if (mark.equals(marks.get(i))) {
                kept.add(verses.get(i));
            }
        }
        return List.copyOf(kept);
    }

    static String mark(SongCfg song, String requestedSeason, String requestedSunday, VerseContext context) {
        if (song.verseSeasons() != null) {
            if (requestedSeason != null) {
                return requestedSeason;
            }
            if (context != null && context.season() != null) {
                return context.season();
            }
            return "comum";
        }
        if (song.verseSundays() != null) {
            if (requestedSunday != null) {
                return requestedSunday;
            }
            if (context != null && context.dated()) {
                return context.sunday();
            }
            return song.verseSundays().get(0);
        }
        return null;
    }

    static String label(SongCfg song, String mark) {
        if (song.verseSundays() != null) {
            return mark == null ? "este domingo" : Lectionary.label(mark);
        }
        return seasonName(mark);
    }

    static String seasonName(String mark) {
        return switch (mark == null ? "" : mark) {
            case "comum" -> "Tempo Comum";
            case "advento" -> "Advento";
            case "natal" -> "Natal";
            case "quaresma" -> "Quaresma";
            case "ramos" -> "Ramos";
            case "pascoa" -> "Páscoa";
            case "pentecostes" -> "Pentecostes";
            default -> mark == null ? "Tempo Comum" : mark;
        };
    }
}
