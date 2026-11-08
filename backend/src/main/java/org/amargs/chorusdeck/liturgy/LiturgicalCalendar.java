package org.amargs.chorusdeck.liturgy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The Sundays and parish solemnities of one liturgical year, each with its date. */
public final class LiturgicalCalendar {

    public record Celebration(LocalDate date, String key, String name, String ano) {
    }

    private LiturgicalCalendar() {
    }

    /** The year that contains {@code reference}, from its First Sunday of Advent through the next. */
    public static List<Celebration> yearOf(LocalDate reference) {
        int year = missalYear(reference);
        String cycle = LiturgicalYear.cycle(reference);
        LocalDate easter = LiturgyClient.easter(year);
        LocalDate advent = LiturgicalYear.firstSundayOfAdvent(year - 1);
        List<Celebration> days = new ArrayList<>();
        add(days, "advent-1", advent, cycle);
        add(days, "advent-2", advent.plusWeeks(1), cycle);
        add(days, "advent-3", advent.plusWeeks(2), cycle);
        add(days, "advent-4", advent.plusWeeks(3), cycle);
        addFixed(days, "immaculate-conception", LocalDate.of(year - 1, 12, 8));
        LocalDate christmas = LocalDate.of(year - 1, 12, 25);
        add(days, "christmas", christmas, null);
        LocalDate holyFamily = christmas.getDayOfWeek() == DayOfWeek.SUNDAY
                ? LocalDate.of(year - 1, 12, 30)
                : christmas.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        add(days, "holy-family", holyFamily, cycle);
        addFixed(days, "mary-mother-of-god", LocalDate.of(year, 1, 1));
        LocalDate epiphany = sundayOnOrAfter(LocalDate.of(year, 1, 2));
        add(days, "epiphany", epiphany, cycle);
        LocalDate baptism = epiphany.plusWeeks(1);
        add(days, "baptism-of-the-lord", baptism, cycle);
        LocalDate lent1 = easter.minusDays(42);
        LocalDate ordinary = baptism.plusWeeks(1);
        for (int number = 2; ordinary.isBefore(lent1); number++) {
            add(days, "ordinary-" + number, ordinary, cycle);
            ordinary = ordinary.plusWeeks(1);
        }
        for (int lent = 1; lent <= 5; lent++) {
            add(days, "lent-" + lent, easter.minusDays(7L * (7 - lent)), cycle);
        }
        add(days, "palm-sunday", easter.minusDays(7), cycle);
        add(days, "easter-sunday", easter, cycle);
        for (int number = 2; number <= 7; number++) {
            add(days, "easter-" + number, easter.plusDays(7L * (number - 1)), cycle);
        }
        add(days, "pentecost", easter.plusDays(49), cycle);
        LocalDate trinity = easter.plusDays(56);
        add(days, "trinity", trinity, cycle);
        add(days, "corpus-christi", easter.plusDays(60), cycle);
        addFixed(days, "john-the-baptist", LocalDate.of(year, 6, 24));
        addFixed(days, "peter-paul", LocalDate.of(year, 6, 29));
        LocalDate christKing = LiturgicalYear.firstSundayOfAdvent(year).minusWeeks(1);
        for (LocalDate resumed = trinity.plusWeeks(1); !resumed.isAfter(christKing); resumed = resumed.plusWeeks(1)) {
            long weeks = ChronoUnit.WEEKS.between(resumed, christKing);
            add(days, "ordinary-" + (34 - weeks), resumed, cycle);
        }
        addFixed(days, "assumption", LocalDate.of(year, 8, 15));
        addFixed(days, "all-saints", LocalDate.of(year, 11, 1));
        days.sort(Comparator.comparing(Celebration::date).thenComparing(Celebration::key));
        return List.copyOf(days);
    }

    /** The civil year that names the liturgical year: 2026 is A, and Advent 2026 begins 2027. */
    static int missalYear(LocalDate date) {
        int year = date.getYear();
        if (!date.isBefore(LiturgicalYear.firstSundayOfAdvent(year))) {
            year++;
        }
        return year;
    }

    private static LocalDate sundayOnOrAfter(LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return date;
        }
        return date.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
    }

    private static void add(List<Celebration> days, String id, LocalDate date, String cycle) {
        String key = cycle == null ? id : id + "/" + cycle;
        Lectionary.Entry entry = Lectionary.find(key);
        if (entry == null) {
            throw new IllegalStateException("Missing lectionary row " + key);
        }
        days.add(new Celebration(date, key, Lectionary.menuName(id), entry.ano()));
    }

    private static void addFixed(List<Celebration> days, String key, LocalDate date) {
        Lectionary.Entry entry = Lectionary.find(key);
        if (entry == null) {
            throw new IllegalStateException("Missing lectionary row " + key);
        }
        days.add(new Celebration(date, key, Lectionary.menuName(key), entry.ano()));
    }
}
