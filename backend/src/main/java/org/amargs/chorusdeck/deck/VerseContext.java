package org.amargs.chorusdeck.deck;

import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.liturgy.Lectionary;
import org.amargs.chorusdeck.liturgy.LiturgicalCalendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The tempo or Sunday a deck is using when a song's text boxes are marked. */
public record VerseContext(String season, String sunday) {

    public static final VerseContext NONE = new VerseContext(null, null);

    public static VerseContext forDate(LocalDate date) {
        if (date == null) {
            return NONE;
        }
        String sunday = LiturgicalCalendar.sundayKey(date);
        Lectionary.Entry entry = Lectionary.find(sunday);
        String season = entry == null || entry.season() == null || entry.season().isBlank()
                ? "comum"
                : entry.season();
        return new VerseContext(season, sunday);
    }

    public boolean dated() {
        return season != null || sunday != null;
    }
}

