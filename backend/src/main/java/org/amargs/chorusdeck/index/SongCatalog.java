package org.amargs.chorusdeck.index;

import com.fasterxml.jackson.databind.JsonNode;
import org.amargs.chorusdeck.liturgy.Lectionary;

import java.util.ArrayList;
import java.util.List;

/** Creates or replaces one song in the index the app is using. */
public final class SongCatalog {

    private SongCatalog() {
    }

    public record Saved(List<SongCfg> songs, SongCfg song) {
    }

    public static SongCfg find(List<SongCfg> songs, int id) {
        for (SongCfg song : songs) {
            if (song.id() == id) {
                return song;
            }
        }
        return null;
    }

    public static Saved save(List<SongCfg> songs, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("O cântico tem de ser um objeto.");
        }
        String title = text(body.get("title"));
        if (title == null) {
            throw new IllegalArgumentException("O cântico precisa de um título.");
        }
        Kept content = verses(body.get("content"), true);
        String bookId = text(body.get("bookId"));
        String titleAlt = text(body.get("titleAlt"));
        Kept contentAlt = verses(body.get("contentAlt"), false);
        String lang = language(body.get("lang"));
        String langAlt = language(body.get("langAlt"));
        String defaultLyrics = defaultLyrics(body.get("defaultLyrics"));
        if (contentAlt.lines() != null && lang != null && lang.equals(langAlt)) {
            throw new IllegalArgumentException("As duas línguas têm de ser diferentes.");
        }
        List<String> verseSeasons = align(body.get("verseSeasons"), content, false);
        List<String> verseSundays = align(body.get("verseSundays"), content, true);
        if (verseSeasons != null && verseSundays != null) {
            throw new IllegalArgumentException("Um cântico muda com o tempo ou com o domingo.");
        }
        if ((verseSeasons != null || verseSundays != null)
                && contentAlt.lines() != null
                && contentAlt.lines().size() != content.lines().size()) {
            throw new IllegalArgumentException("As duas línguas têm de ter as mesmas caixas de texto.");
        }
        List<SongCfg> next = new ArrayList<>(songs == null ? List.of() : songs);
        JsonNode idNode = body.get("id");
        if (idNode == null || idNode.isNull()) {
            int id = nextId(next);
            SongCfg song = new SongCfg(
                    id, title, content.lines(), bookId, titleAlt, contentAlt.lines(),
                    lang, langAlt, defaultLyrics, verseSeasons, verseSundays);
            next.add(song);
            return new Saved(next, song);
        }
        if (!idNode.isIntegralNumber() || idNode.intValue() < 1) {
            throw new IllegalArgumentException("O número do cântico começa em 1.");
        }
        int id = idNode.intValue();
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id() == id) {
                SongCfg song = new SongCfg(
                        id, title, content.lines(), bookId, titleAlt, contentAlt.lines(),
                        lang, langAlt, defaultLyrics, verseSeasons, verseSundays);
                next.set(i, song);
                return new Saved(next, song);
            }
        }
        throw new IllegalArgumentException("Cântico desconhecido.");
    }

    private record Kept(List<String> lines, List<Integer> indexes, int raw) {
    }

    private static Kept verses(JsonNode node, boolean required) {
        if (node == null || node.isNull()) {
            if (required) {
                throw new IllegalArgumentException("O cântico precisa das estrofes.");
            }
            return new Kept(null, List.of(), 0);
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("O cântico precisa das estrofes.");
        }
        List<String> content = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode verse = node.get(i);
            if (!verse.isTextual()) {
                throw new IllegalArgumentException("Cada estrofe é texto.");
            }
            String text = verse.asText().strip();
            if (!text.isEmpty()) {
                content.add(text);
                indexes.add(i);
            }
        }
        if (content.isEmpty() && !required) {
            return new Kept(null, List.of(), node.size());
        }
        return new Kept(content, indexes, node.size());
    }

    /** Marks follow the text boxes that were kept. An absent list stays absent. */
    private static List<String> align(JsonNode node, Kept verses, boolean sunday) {
        if (node == null || node.isNull() || node.isEmpty()) {
            return null;
        }
        if (!node.isArray() || node.size() != verses.raw()) {
            throw new IllegalArgumentException("As marcas têm de acompanhar as caixas de texto.");
        }
        if (verses.indexes().isEmpty()) {
            return null;
        }
        String missing = sunday ? "Cada caixa de texto precisa de um domingo." : "Cada caixa de texto precisa de um tempo.";
        List<String> marks = new ArrayList<>();
        for (int index : verses.indexes()) {
            JsonNode item = node.get(index);
            if (item == null || !item.isTextual() || item.asText().isBlank()) {
                throw new IllegalArgumentException(missing);
            }
            String text = item.asText().strip();
            if (sunday) {
                if (Lectionary.find(text) == null) {
                    throw new IllegalArgumentException("Este domingo não faz parte do ano litúrgico.");
                }
            } else if (!SongCfg.SEASONS.contains(text)) {
                throw new IllegalArgumentException("O tempo não é conhecido.");
            }
            marks.add(text);
        }
        return List.copyOf(marks);
    }

    private static int nextId(List<SongCfg> songs) {
        int next = 1;
        for (SongCfg song : songs) {
            if (song.id() >= next) {
                next = song.id() + 1;
            }
        }
        return next;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("O título, o título alternativo e o número são texto.");
        }
        String text = node.asText().strip();
        return text.isEmpty() ? null : text;
    }

    private static String language(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException(
                    "A língua tem de ser português, inglês, espanhol, francês ou latim.");
        }
        String code = node.asText().strip();
        if (code.isEmpty()) {
            return null;
        }
        if (!SongCfg.LANGUAGES.contains(code)) {
            throw new IllegalArgumentException(
                    "A língua tem de ser português, inglês, espanhol, francês ou latim.");
        }
        return code;
    }

    private static String defaultLyrics(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("A língua por omissão tem de ser uma das línguas do cântico.");
        }
        String value = node.asText().strip();
        if (value.isEmpty() || value.equals("content")) {
            return null;
        }
        if (value.equals("contentAlt")) {
            return value;
        }
        throw new IllegalArgumentException("A língua por omissão tem de ser uma das línguas do cântico.");
    }
}
