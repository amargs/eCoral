package org.amargs.chorusdeck.deck;

import com.fasterxml.jackson.databind.JsonNode;
import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.liturgy.Lectionary;

import java.util.ArrayList;
import java.util.List;

/** Which language, and which text boxes, one deck shows for a song. The slide title is always the main title. */
public record SlideChoice(String title, String lyrics, List<Integer> slides, String season, String sunday) {

    public SlideChoice(String title, String lyrics) {
        this(title, lyrics, null, null, null);
    }

    public SlideChoice(String title, String lyrics, List<Integer> slides) {
        this(title, lyrics, slides, null, null);
    }

    public SlideChoice {
        if (title != null && !title.equals("title") && !title.equals("titleAlt")) {
            throw new IllegalArgumentException("title must be title or titleAlt.");
        }
        title = null;
        if (lyrics != null && !lyrics.equals("content") && !lyrics.equals("contentAlt")) {
            throw new IllegalArgumentException("lyrics must be content or contentAlt.");
        }
        if (slides != null) {
            if (slides.isEmpty()) {
                throw new IllegalArgumentException("slides must list the text boxes to keep.");
            }
            int previous = 0;
            for (Integer slide : slides) {
                if (slide == null || slide <= previous) {
                    throw new IllegalArgumentException("slides must be increasing positions starting at 1.");
                }
                previous = slide;
            }
            slides = List.copyOf(slides);
        }
        if (season != null && !SongCfg.SEASONS.contains(season)) {
            throw new IllegalArgumentException("Unknown season: " + season);
        }
        if (sunday != null && Lectionary.find(sunday) == null) {
            throw new IllegalArgumentException("Unknown Sunday: " + sunday);
        }
        if (season != null && sunday != null) {
            throw new IllegalArgumentException("A song choice has a tempo or a Sunday.");
        }
    }

    public boolean isDefault() {
        return lyrics == null && slides == null && season == null && sunday == null;
    }

    public static SlideChoice from(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("A song choice must be an object.");
        }
        SlideChoice choice = new SlideChoice(
                text(node, "title"), text(node, "lyrics"), slides(node), text(node, "season"), text(node, "sunday"));
        return choice.isDefault() ? null : choice;
    }

    public static SongCfg resolve(SongCfg song, SlideChoice choice, VerseContext context) {
        SlideChoice used = choice == null ? new SlideChoice(null, null) : choice;
        return used.show(song, context == null ? VerseContext.NONE : context);
    }

    /** Names the song and the tempo or Sunday that had no text box. */
    public static String leftOut(SongCfg song, SlideChoice choice, VerseContext context) {
        SlideChoice used = choice == null ? new SlideChoice(null, null) : choice;
        String mark = VerseBoxes.mark(song, used.season, used.sunday, context);
        String name = song.bookId() == null || song.bookId().isBlank()
                ? song.title().trim()
                : song.bookId().trim() + " " + song.title().trim();
        return name + " has no text box for " + VerseBoxes.label(song, mark) + ". It is left out.";
    }

    /** The verses the slides use, under the main title. The library song is left as it was. */
    public SongCfg show(SongCfg song) {
        return show(song, VerseContext.NONE);
    }

    /**
     * The verses of the chosen language that belong to this tempo or Sunday.
     * Null when that tempo or Sunday has no text box: the deck leaves the song out.
     */
    public SongCfg show(SongCfg song, VerseContext context) {
        VerseContext used = context == null ? VerseContext.NONE : context;
        boolean second = "contentAlt".equals(lyrics)
                || (lyrics == null && "contentAlt".equals(song.defaultLyrics()));
        List<String> verses;
        if (second) {
            if (song.contentAlt() == null || song.contentAlt().isEmpty()) {
                throw new IllegalArgumentException("Song " + song.id() + " has no second language.");
            }
            verses = song.contentAlt();
        } else {
            verses = song.content() == null ? List.of() : song.content();
        }
        if (song.verseSeasons() != null || song.verseSundays() != null) {
            String mark = VerseBoxes.mark(song, season, sunday, used);
            if (song.verseSeasons() != null && mark != null && !SongCfg.SEASONS.contains(mark)) {
                throw new IllegalArgumentException("Unknown season: " + mark);
            }
            if (song.verseSundays() != null && mark != null && Lectionary.find(mark) == null) {
                throw new IllegalArgumentException("Unknown Sunday: " + mark);
            }
            List<String> marks = song.verseSeasons() != null ? song.verseSeasons() : song.verseSundays();
            verses = mark == null ? List.of() : VerseBoxes.of(verses, marks, mark);
            if (verses.isEmpty()) {
                return null;
            }
        }
        verses = keep(song.id(), verses, slides);
        if (verses.isEmpty()) {
            throw new IllegalArgumentException("Song " + song.id() + " has no verses.");
        }
        return new SongCfg(song.id(), song.title(), verses, song.bookId(), song.titleAlt(), null);
    }

    private static List<String> keep(int id, List<String> verses, List<Integer> slides) {
        if (slides == null) {
            return List.copyOf(verses);
        }
        List<String> kept = new ArrayList<>();
        for (int slide : slides) {
            if (slide > verses.size()) {
                throw new IllegalArgumentException("Song " + id + " has no verse " + slide + ".");
            }
            kept.add(verses.get(slide - 1));
        }
        return List.copyOf(kept);
    }

    private static List<Integer> slides(JsonNode node) {
        JsonNode value = node.get("slides");
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray() || value.isEmpty()) {
            throw new IllegalArgumentException("slides must list the text boxes to keep.");
        }
        List<Integer> slides = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isIntegralNumber()) {
                throw new IllegalArgumentException("slides must be increasing positions starting at 1.");
            }
            slides.add(item.intValue());
        }
        return slides;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text.");
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }
}
