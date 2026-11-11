package org.amargs.chorusdeck.index;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.amargs.chorusdeck.liturgy.Lectionary;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SongCfg(
        int id,
        String title,
        List<String> content,
        String bookId,
        String titleAlt,
        List<String> contentAlt,
        String lang,
        String langAlt,
        String defaultLyrics,
        List<String> verseSeasons,
        List<String> verseSundays) {

    public static final Set<String> LANGUAGES = Set.of("pt", "en", "es", "fr", "la");

    public static final List<String> SEASONS = List.of(
            "comum", "advento", "natal", "quaresma", "ramos", "pascoa", "pentecostes");

    public SongCfg {
        if (titleAlt != null) {
            titleAlt = titleAlt.strip();
            if (titleAlt.isEmpty()) {
                titleAlt = null;
            }
        }
        if (contentAlt != null && contentAlt.isEmpty()) {
            contentAlt = null;
        }
        if (contentAlt == null) {
            lang = null;
            langAlt = null;
            defaultLyrics = null;
        } else {
            if (lang == null || !LANGUAGES.contains(lang)) {
                lang = "pt";
            }
            if (langAlt == null || !LANGUAGES.contains(langAlt) || langAlt.equals(lang)) {
                langAlt = "la".equals(lang) ? "pt" : "la";
            }
            if (!"contentAlt".equals(defaultLyrics)) {
                defaultLyrics = null;
            }
        }
        verseSeasons = seasons(verseSeasons);
        verseSundays = sundays(verseSundays);
        if (verseSeasons != null && verseSundays != null) {
            throw new IllegalArgumentException("Um cântico muda com o tempo ou com o domingo.");
        }
        int rows = rows(content, contentAlt);
        if (verseSeasons != null && verseSeasons.size() != rows) {
            throw new IllegalArgumentException("Cada caixa de texto precisa de um tempo.");
        }
        if (verseSundays != null && verseSundays.size() != rows) {
            throw new IllegalArgumentException("Cada caixa de texto precisa de um domingo.");
        }
    }

    public SongCfg(int id, String title, List<String> content) {
        this(id, title, content, null, null, null, null, null, null, null, null);
    }

    public SongCfg(int id, String title, List<String> content, String bookId) {
        this(id, title, content, bookId, null, null, null, null, null, null, null);
    }

    public SongCfg(
            int id,
            String title,
            List<String> content,
            String bookId,
            String titleAlt,
            List<String> contentAlt) {
        this(id, title, content, bookId, titleAlt, contentAlt, null, null, null, null, null);
    }

    public SongCfg(
            int id,
            String title,
            List<String> content,
            String bookId,
            String titleAlt,
            List<String> contentAlt,
            String lang,
            String langAlt) {
        this(id, title, content, bookId, titleAlt, contentAlt, lang, langAlt, null, null, null);
    }

    public SongCfg(
            int id,
            String title,
            List<String> content,
            String bookId,
            String titleAlt,
            List<String> contentAlt,
            String lang,
            String langAlt,
            String defaultLyrics) {
        this(id, title, content, bookId, titleAlt, contentAlt, lang, langAlt, defaultLyrics, null, null);
    }

    /** The verses a deck uses when the request does not choose a language. The text-box marks stay. */
    public SongCfg prepared() {
        if (!"contentAlt".equals(defaultLyrics) || contentAlt == null || contentAlt.isEmpty()) {
            return this;
        }
        int rows = contentAlt.size();
        return new SongCfg(
                id,
                title,
                List.copyOf(contentAlt),
                bookId,
                titleAlt,
                null,
                null,
                null,
                null,
                sized(verseSeasons, rows),
                sized(verseSundays, rows));
    }

    /**
     * The same song after a re-index or a new songbook number.
     * Text-box marks stay when they still line up with the verses.
     */
    public SongCfg retain(int id, String title, List<String> content, String bookId) {
        int rows = content == null ? 0 : content.size();
        List<String> seasons = sized(verseSeasons, rows);
        List<String> sundays = seasons == null ? sized(verseSundays, rows) : null;
        List<String> alt = contentAlt;
        String keptLang = lang;
        String keptLangAlt = langAlt;
        String keptDefault = defaultLyrics;
        if ((seasons != null || sundays != null) && alt != null && alt.size() != rows) {
            alt = null;
            keptLang = null;
            keptLangAlt = null;
            keptDefault = null;
        }
        return new SongCfg(
                id, title, content, bookId, titleAlt, alt, keptLang, keptLangAlt, keptDefault, seasons, sundays);
    }

    private static List<String> sized(List<String> marks, int rows) {
        if (marks == null || marks.size() != rows) {
            return null;
        }
        return marks;
    }

    private static int rows(List<String> content, List<String> contentAlt) {
        int primary = content == null ? 0 : content.size();
        int alt = contentAlt == null ? 0 : contentAlt.size();
        return Math.max(primary, alt);
    }

    private static List<String> seasons(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        List<String> marks = new ArrayList<>();
        for (String mark : raw) {
            if (mark == null || mark.isBlank() || !SEASONS.contains(mark)) {
                throw new IllegalArgumentException("O tempo não é conhecido.");
            }
            marks.add(mark);
        }
        return List.copyOf(marks);
    }

    private static List<String> sundays(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        List<String> marks = new ArrayList<>();
        for (String mark : raw) {
            if (mark == null || mark.isBlank() || Lectionary.find(mark) == null) {
                throw new IllegalArgumentException("Este domingo não faz parte do ano litúrgico.");
            }
            marks.add(mark);
        }
        return List.copyOf(marks);
    }
}
