package org.amargs.chorusdeck.deck;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.index.SongIndex;
import org.amargs.chorusdeck.liturgy.Lectionary;
import org.amargs.chorusdeck.liturgy.LiturgicalCalendar;
import org.amargs.chorusdeck.liturgy.LiturgyDay;
import org.amargs.chorusdeck.web.SongDeckRequests;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;
import org.apache.poi.sl.usermodel.Placeholder;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlideChoiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void theSlidesShowOnlyTheChosenTitleLanguageAndVerses() throws Exception {
        Path dir = Files.createTempDirectory("slide-choice");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(new SongCfg(
                1,
                "Pelos caminhos",
                List.of("um", "dois", "três"),
                "12",
                "Per viam",
                List.of("unus", "duo"))));
        String before = Files.readString(index);
        Path out = dir.resolve("canticos.pptx");
        AppConfig config = new AppConfig(Path.of("unused.pptx"), index, template(), out);
        SlideChoice choice = new SlideChoice("titleAlt", "contentAlt");
        new DeckService().generateSongs(
                config,
                new DeckService.Selection.Ids(List.of(1), List.of(choice)),
                null);

        try (XMLSlideShow ppt = open(out)) {
            assertEquals(2, ppt.getSlides().size());
            assertEquals("Pelos caminhos", textShape(ppt.getSlides().get(0), "Título").getText());
            assertEquals("unus", textShape(ppt.getSlides().get(0), "Letra").getText());
            assertTrue(slideText(ppt).contains("duo"));
            assertFalse(slideText(ppt).contains("Per viam"));
        }
        assertEquals(before, Files.readString(index));

        Path noted = dir.resolve("noted.pptx");
        new DeckService().generateSongs(
                new AppConfig(Path.of("unused.pptx"), index, template(), noted),
                new DeckService.Selection.Ids(List.of(1), List.of(choice)),
                new Cover("comum", null, "Domingo", "frase", "Jo 1, 1", "A"));
        try (XMLSlideShow ppt = open(noted)) {
            assertEquals("12 - Pelos caminhos (Per viam)", notes(ppt.getSlides().get(0)));
            assertEquals("Pelos caminhos", textShape(ppt.getSlides().get(2), "Título").getText());
        }

        Path massOut = dir.resolve("missa.pptx");
        Path yaml = dir.resolve("missa.yaml");
        Files.writeString(yaml, """
                season: comum
                ano: A
                domingo: Domingo
                frase: frase
                versiculo: Jo 1, 1
                output: missa.pptx
                songs:
                  entrada: "12"
                opcoes:
                  credo: breve
                  interval: vazio
                """);
        new DeckService().generateMass(
                new AppConfig(Path.of("unused.pptx"), index, template(), massOut),
                MassRequest.load(yaml),
                java.util.Map.of("entrada", new SlideChoice("title", "content")));
        try (XMLSlideShow ppt = open(massOut)) {
            String text = slideText(ppt);
            assertTrue(text.contains("Pelos caminhos"));
            assertTrue(text.contains("um"));
            assertTrue(text.contains("dois"));
            assertTrue(text.contains("três"));
            assertFalse(text.contains("Per viam"));
            assertFalse(text.contains("unus"));
        }
        assertEquals(before, Files.readString(index));
    }

    @Test
    void theCatalogueWritesEveryLanguageOnTheOrdinaryLayouts() throws Exception {
        Path dir = Files.createTempDirectory("catalogue");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(
                new SongCfg(1, "Pelos caminhos", List.of("um", "dois"), "12", "Per viam", List.of("unus")),
                new SongCfg(2, "Sem letra", List.of(), "4"),
                new SongCfg(3, "Só latim", List.of(), "A1", null, List.of("duo"))));
        String before = Files.readString(index);
        Path out = dir.resolve("canticos.pptx");
        new DeckService().generateCatalogue(new AppConfig(Path.of("unused.pptx"), index, template(), out));

        try (XMLSlideShow ppt = open(out)) {
            assertEquals(4, ppt.getSlides().size());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(0).getSlideLayout().getName());
            assertEquals("Pelos caminhos", textShape(ppt.getSlides().get(0), "Título").getText());
            assertEquals("um", textShape(ppt.getSlides().get(0), "Letra").getText());
            assertEquals(TemplateLayouts.LYRICS, ppt.getSlides().get(1).getSlideLayout().getName());
            assertEquals("dois", textShape(ppt.getSlides().get(1), "Letra").getText());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(2).getSlideLayout().getName());
            assertEquals("Pelos caminhos", textShape(ppt.getSlides().get(2), "Título").getText());
            assertEquals("unus", textShape(ppt.getSlides().get(2), "Letra").getText());
            assertEquals(TemplateLayouts.ORDINARY_TITLE, ppt.getSlides().get(3).getSlideLayout().getName());
            assertEquals("Só latim", textShape(ppt.getSlides().get(3), "Título").getText());
            assertEquals("duo", textShape(ppt.getSlides().get(3), "Letra").getText());
            assertEquals("accent1", headerScheme(ppt));
            assertFalse(slideText(ppt).contains("Per viam"));
            assertFalse(slideText(ppt).contains("Sem letra"));
            for (XSLFSlide slide : ppt.getSlides()) {
                String layout = slide.getSlideLayout().getName();
                assertFalse(TemplateLayouts.COVER.equals(layout));
                assertFalse(layout.startsWith("Transição"));
            }
        }
        assertEquals(before, Files.readString(index));

        Path empty = dir.resolve("empty.json");
        SongIndex.write(empty, List.of(new SongCfg(2, "Sem letra", List.of())));
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new DeckService().generateCatalogue(
                        new AppConfig(Path.of("unused.pptx"), empty, template(), dir.resolve("vazio.pptx"))));
        assertTrue(error.getMessage().contains("Não há cânticos com letra."));
        assertFalse(Files.exists(dir.resolve("vazio.pptx")));
    }

    @Test
    void theCoverCatalogueKeepsEachCover() throws Exception {
        Path dir = Files.createTempDirectory("covers");
        Path out = dir.resolve("capas.pptx");
        new DeckService().generateCovers(new AppConfig(Path.of("unused.pptx"), dir.resolve("index.json"), template(), out));

        try (XMLSlideShow ppt = open(out)) {
            assertEquals(Season.coverLayouts().size(), ppt.getSlides().size());
            XSLFSlide ordinary = ppt.getSlides().get(0);
            assertEquals(TemplateLayouts.COVER, ordinary.getSlideLayout().getName());
            assertEquals("Domingo XXIX", textShape(ordinary, "Domingo").getText());
            assertEquals("«Bem-aventurados os pobres em espírito»", textShape(ordinary, "Frase").getText());
            assertEquals("Mt 5, 3", textShape(ordinary, "Versiculo").getText());
            assertEquals("Tempo Comum · Ano A", textShape(ordinary, "Tempo").getText());
            String ordinaryBg = ordinary.getXmlObject().getCSld().getBg().xmlText();
            assertTrue(ordinaryBg.contains("accent1"), ordinaryBg);

            XSLFSlide advent = ppt.getSlides().get(1);
            assertEquals("Domingo II", textShape(advent, "Domingo").getText());
            assertEquals("Tempo de Advento · Ano A", textShape(advent, "Tempo").getText());
            String adventBg = advent.getXmlObject().getCSld().getBg().xmlText();
            assertTrue(adventBg.contains("accent2"), adventBg);
            assertEquals(1, pictureCount(ordinary));
            assertEquals(0, pictureCount(ordinary.getSlideLayout()));
            assertArrayEquals(logo("logo_comum.png"), slideLogo(ordinary));
            assertArrayEquals(logo("logo_advento.png"), slideLogo(advent));
            assertEquals(1, pictureCount(advent));

            XSLFSlide taize = ppt.getSlides().get(ppt.getSlides().size() - 1);
            assertEquals("Oração de Taizé", textShape(taize, "Domingo").getText());
            assertEquals("29/03/2014 | Paróquia de Esgueira", textShape(taize, "Tempo").getText());
            assertTrue(taize.getXmlObject().getCSld().getBg().xmlText().toLowerCase().contains("e37931"));
        }
    }

    @Test
    void aSongRequestCarriesTheChoiceAndUsesTheDefaultLanguage() throws Exception {
        List<SongCfg> songs = List.of(new SongCfg(
                1, "Pelos caminhos", List.of("um", "dois"), "12", "Per viam", List.of("unus")));
        Path dir = Files.createTempDirectory("slide-choice");
        SongDeckRequests.Ready ready = SongDeckRequests.parse(JSON.readTree("""
                {"ids":[{"id":1,"title":"titleAlt","lyrics":"contentAlt"}],"output":"a.pptx"}
                """), songs, dir);
        assertEquals(List.of(1), ready.ids());
        assertEquals(List.of(new SlideChoice("titleAlt", "contentAlt")), ready.choices());

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> SongDeckRequests.parse(JSON.readTree("""
                {"ids":[1],"output":"a.pptx"}
                """), List.of(new SongCfg(1, "Só latim", List.of(), "A1", null, List.of("unus"))), dir));
        assertTrue(missing.getMessage().contains("no verses"));

        SongCfg latinFirst = new SongCfg(
                1, "Pelos caminhos", List.of("um", "dois"), "12", "Per viam", List.of("unus"), "pt", "la", "contentAlt");
        SongDeckRequests.Ready bare = SongDeckRequests.parse(JSON.readTree("""
                {"ids":[1],"output":"a.pptx"}
                """), List.of(latinFirst), dir);
        assertNull(bare.choices().get(0));
        assertEquals(List.of("unus"), latinFirst.prepared().content());
        assertEquals("Pelos caminhos", latinFirst.prepared().title());
        assertEquals("Per viam", latinFirst.prepared().titleAlt());
        assertEquals(List.of("um", "dois"), new SlideChoice(null, "content").show(latinFirst).content());
        assertEquals(List.of("unus"), new SlideChoice(null, null).show(latinFirst).content());
    }

    @Test
    void aShorterChoiceKeepsThoseTextBoxes() throws Exception {
        List<String> verses = List.of("**Aleluia**", "Escutai", "**Aleluia**", "Glória", "**Aleluia**");
        SongCfg song = new SongCfg(11, "Aleluia, já nasceu o Salvador", verses, "10");
        assertEquals(List.of("**Aleluia**", "Escutai", "**Aleluia**"), new SlideChoice(null, null, List.of(1, 2, 3)).show(song).content());
        assertEquals(verses, song.content());

        Path dir = Files.createTempDirectory("slide-choice");
        SongDeckRequests.Ready ready = SongDeckRequests.parse(JSON.readTree("""
                {"ids":[{"id":11,"slides":[1,2,3]}],"output":"a.pptx"}
                """), List.of(song), dir);
        assertEquals(List.of(1, 2, 3), ready.choices().get(0).slides());
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> SongDeckRequests.parse(JSON.readTree("""
                {"ids":[{"id":11,"slides":[1,6]}],"output":"a.pptx"}
                """), List.of(song), dir));
        assertTrue(missing.getMessage().contains("no verse 6"));

        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(song));
        String before = Files.readString(index);
        Path out = dir.resolve("canticos.pptx");
        new DeckService().generateSongs(
                new AppConfig(Path.of("unused.pptx"), index, template(), out),
                new DeckService.Selection.Ids(List.of(11), ready.choices()),
                null);
        try (XMLSlideShow ppt = open(out)) {
            assertEquals(3, ppt.getSlides().size());
            assertEquals("Escutai", textShape(ppt.getSlides().get(1), "Letra").getText());
            assertFalse(slideText(ppt).contains("Glória"));
        }
        assertEquals(before, Files.readString(index));
    }

    @Test
    void aTempoOrSundayKeepsOnlyThoseTextBoxes() throws Exception {
        SongCfg alleluia = new SongCfg(
                5, "Aleluia Glória ao Senhor",
                List.of("**Aleluia.**\nVerso da Páscoa", "**Aleluia.**\nVerso do Natal"),
                "A5", null, null, null, null, null, List.of("pascoa", "natal"), null);
        SongCfg louvor = new SongCfg(
                6, "Louvor a vós",
                List.of("Estrofe do ano A", "Estrofe do ano B"),
                "L1", null, null, null, null, null, null, List.of("lent-1/A", "lent-3/B"));
        SongCfg plain = new SongCfg(1, "Pelos caminhos", List.of("um"), "12");
        SongCfg parted = new SongCfg(
                8, "Partilhai",
                List.of("Primeira estrofe do comum", "**Refrão**", "Segunda estrofe do comum", "Estrofe da Páscoa"),
                "20", null, null, null, null, null,
                List.of("comum", "comum", "comum", "pascoa"), null);

        assertNull(new SlideChoice(null, null, null, "quaresma", null).show(alleluia));
        VerseContext ordinary = VerseContext.forDate(dateOf("ordinary-29/A"));
        assertEquals(List.of("**Aleluia.**\nVerso do Natal"),
                new SlideChoice(null, null, null, "natal", null).show(alleluia, ordinary).content());
        assertNull(new SlideChoice(null, null).show(alleluia, ordinary));
        VerseContext lent = VerseContext.forDate(dateOf("lent-1/A"));
        assertEquals(List.of("Estrofe do ano B"),
                new SlideChoice(null, null, null, null, "lent-3/B").show(louvor, lent).content());
        assertEquals(
                List.of("**Aleluia.**\nVerso da Páscoa"),
                new SlideChoice(null, null, null, "pascoa", null).show(alleluia).content());
        assertEquals(
                List.of("Estrofe do ano B"),
                new SlideChoice(null, null, null, null, "lent-3/B").show(louvor).content());
        assertEquals(List.of("um"), new SlideChoice(null, null, null, "pascoa", null).show(plain).content());
        assertEquals(
                List.of("Primeira estrofe do comum"),
                new SlideChoice(null, null, List.of(1), "comum", null).show(parted).content());
        assertEquals(List.of("comum", "comum", "comum", "pascoa"), parted.verseSeasons());

        assertEquals(List.of("pascoa", "natal"), SongDeckRequests.songs(List.of(alleluia)).get(0).verseSeasons());
        assertNull(SongDeckRequests.songs(List.of(alleluia)).get(0).estrofesByMark());
        assertEquals("Domingo III da Quaresma · Ano B", SongDeckRequests.songs(List.of(louvor)).get(0).verseSundayNames().get("lent-3/B"));

        Path dir = Files.createTempDirectory("verse-marks");
        Path index = dir.resolve("index.json");
        SongIndex.write(index, List.of(alleluia, louvor, plain));
        String before = Files.readString(index);
        Path natal = dir.resolve("natal.pptx");
        new DeckService().generateSongs(
                new AppConfig(Path.of("unused.pptx"), index, template(), natal),
                new DeckService.Selection.Ids(List.of(5), List.of(new SlideChoice(null, null, null, "natal", null))),
                null);
        try (XMLSlideShow ppt = open(natal)) {
            String text = slideText(ppt);
            assertTrue(text.contains("Verso do Natal"));
            assertFalse(text.contains("Verso da Páscoa"));
        }

        IllegalArgumentException leftOut = assertThrows(IllegalArgumentException.class, () -> new DeckService().generateSongs(
                new AppConfig(Path.of("unused.pptx"), index, template(), dir.resolve("comum.pptx")),
                new DeckService.Selection.Ids(List.of(5), List.of()),
                null));
        assertTrue(leftOut.getMessage().contains("Aleluia Glória ao Senhor"));
        assertTrue(leftOut.getMessage().contains("Tempo Comum"));

        Path easterOut = dir.resolve("pascoa.pptx");
        new DeckService().generateMass(
                new AppConfig(Path.of("unused.pptx"), index, template(), easterOut),
                mass(dir.resolve("pascoa.yaml"), "easter-sunday/A", "A5"));
        try (XMLSlideShow ppt = open(easterOut)) {
            String text = slideText(ppt);
            assertTrue(text.contains("Verso da Páscoa"));
            assertFalse(text.contains("Verso do Natal"));
        }

        Path ordinaryOut = dir.resolve("comum-missa.pptx");
        new DeckService().generateMass(
                new AppConfig(Path.of("unused.pptx"), index, template(), ordinaryOut),
                mass(dir.resolve("comum.yaml"), "ordinary-29/A", "A5"));
        try (XMLSlideShow ppt = open(ordinaryOut)) {
            String text = slideText(ppt);
            assertFalse(text.contains("Verso da Páscoa"));
            assertFalse(text.contains("Verso do Natal"));
        }

        Path lentOut = dir.resolve("quaresma.pptx");
        new DeckService().generateMass(
                new AppConfig(Path.of("unused.pptx"), index, template(), lentOut),
                mass(dir.resolve("quaresma.yaml"), "lent-3/B", "L1"));
        try (XMLSlideShow ppt = open(lentOut)) {
            String text = slideText(ppt);
            assertTrue(text.contains("Estrofe do ano B"));
            assertFalse(text.contains("Estrofe do ano A"));
        }
        assertEquals(before, Files.readString(index));

        Path catalogue = dir.resolve("todos.pptx");
        new DeckService().generateCatalogue(new AppConfig(Path.of("unused.pptx"), index, template(), catalogue));
        try (XMLSlideShow ppt = open(catalogue)) {
            String text = slideText(ppt);
            assertTrue(text.contains("Verso da Páscoa"));
            assertTrue(text.contains("Verso do Natal"));
            assertTrue(text.contains("Estrofe do ano A"));
            assertTrue(text.contains("Estrofe do ano B"));
        }
        assertEquals(before, Files.readString(index));
    }

    private static MassRequest mass(Path yaml, String sunday, String book) throws Exception {
        LocalDate date = dateOf(sunday);
        String season = Lectionary.find(sunday).season();
        Files.writeString(yaml, """
                season: %s
                ano: A
                domingo: Domingo
                frase: frase
                versiculo: Jo 1, 1
                output: missa.pptx
                songs:
                  entrada: "%s"
                opcoes:
                  credo: breve
                  interval: vazio
                """.formatted(season, book));
        return MassRequest.load(yaml).withLiturgy(new LiturgyDay(
                date,
                "Domingo",
                season,
                null,
                "A",
                new LiturgyDay.Reading("", ""),
                null,
                "",
                new LiturgyDay.Reading("", ""),
                null));
    }

    private static LocalDate dateOf(String key) {
        for (int year = 2024; year <= 2030; year++) {
            for (LiturgicalCalendar.Celebration day : LiturgicalCalendar.yearOf(LocalDate.of(year, 6, 1))) {
                if (key.equals(day.key())) {
                    return day.date();
                }
            }
        }
        throw new AssertionError(key);
    }

    private static byte[] slideLogo(XSLFSlide slide) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFPictureShape picture) {
                return picture.getPictureData().getData();
            }
        }
        throw new IllegalStateException("Cover logo not found");
    }

    private static int pictureCount(org.apache.poi.xslf.usermodel.XSLFSheet sheet) {
        int count = 0;
        for (XSLFShape shape : sheet.getShapes()) {
            if (shape instanceof XSLFPictureShape) {
                count++;
            }
        }
        return count;
    }

    private static byte[] logo(String name) throws Exception {
        return Files.readAllBytes(template().toAbsolutePath().normalize().getParent().resolve("../../assets").resolve(name).normalize());
    }

    private static String headerScheme(XMLSlideShow ppt) {
        XSLFSlideLayout layout = TemplateLayouts.findLayout(ppt, TemplateLayouts.ORDINARY_TITLE);
        for (XSLFShape shape : layout.getShapes()) {
            if ("Header".equals(shape.getShapeName()) && shape.getXmlObject() instanceof CTShape ctShape) {
                return ctShape.getSpPr().getSolidFill().getSchemeClr().getVal().toString();
            }
        }
        throw new IllegalStateException("Header not found");
    }

    private static String notes(XSLFSlide slide) {
        XSLFNotes notes = slide.getNotes();
        if (notes == null) {
            throw new IllegalStateException("Notes placeholder not found");
        }
        for (XSLFShape shape : notes.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && textShape.getTextType() == Placeholder.BODY) {
                return textShape.getText();
            }
        }
        throw new IllegalStateException("Notes placeholder not found");
    }

    private static String slideText(XMLSlideShow ppt) {
        StringBuilder text = new StringBuilder();
        for (XSLFSlide slide : ppt.getSlides()) {
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape textShape) {
                    text.append(textShape.getText()).append('\n');
                }
            }
        }
        return text.toString();
    }

    private static XSLFTextShape textShape(XSLFSlide slide, String name) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape textShape && name.equals(textShape.getShapeName())) {
                return textShape;
            }
        }
        throw new IllegalStateException("Shape not found: " + name);
    }

    private static XMLSlideShow open(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return new XMLSlideShow(in);
        }
    }

    private static Path template() {
        Path direct = Path.of("template_4x3.pptx");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path data = Path.of("data", "template_4x3.pptx");
        if (Files.isRegularFile(data)) {
            return data;
        }
        Path nested = Path.of("backend", "data", "template_4x3.pptx");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        throw new IllegalStateException("template_4x3.pptx not found");
    }
}
