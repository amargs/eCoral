package org.amargs.chorusdeck.liturgy;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiturgicalCalendarTest {

    /** Dates the parish has already resolved, and the Sunday they belong to. */
    private static final String[][] KNOWN = {
            {"2026-10-18", "ordinary-29/A"},
            {"2026-12-06", "advent-2/B"},
            {"2026-12-13", "advent-3/B"},
            {"2027-03-07", "lent-4/B"},
            {"2027-05-16", "pentecost/B"},
            {"2027-03-21", "palm-sunday/B"},
            {"2027-08-15", "ordinary-20/B"},
            {"2026-02-22", "lent-1/A"},
            {"2026-10-25", "ordinary-30/A"},
            {"2026-10-11", "ordinary-28/A"},
            {"2027-03-14", "lent-5/B"},
            {"2027-04-25", "easter-5/B"},
            {"2027-07-25", "ordinary-17/B"},
            {"2024-12-08", "advent-2/C"},
            {"2024-12-08", "immaculate-conception"},
            {"2026-11-29", "advent-1/B"},
            {"2026-11-01", "ordinary-31/A"},
            {"2026-11-15", "ordinary-33/A"},
            {"2026-12-27", "holy-family/B"},
            {"2027-01-03", "epiphany/B"},
            {"2027-01-17", "ordinary-2/B"},
            {"2027-04-18", "easter-4/B"},
            {"2026-11-08", "ordinary-32/A"},
            {"2026-08-16", "ordinary-20/A"},
            {"2027-01-10", "baptism-of-the-lord/B"},
            {"2027-02-14", "lent-1/B"},
    };

    @Test
    void aKnownDateIsThatSunday() {
        for (String[] row : KNOWN) {
            LocalDate date = LocalDate.parse(row[0]);
            assertTrue(contains(LiturgicalCalendar.yearOf(date), date, row[1]), row[0] + " is " + row[1]);
        }
    }

    @Test
    void adventOpensTheNextYear() {
        List<LiturgicalCalendar.Celebration> before = LiturgicalCalendar.yearOf(LocalDate.parse("2026-11-08"));
        assertFalse(contains(before, LocalDate.parse("2026-11-29"), "advent-1/B"));
        List<LiturgicalCalendar.Celebration> advent = LiturgicalCalendar.yearOf(LocalDate.parse("2026-11-29"));
        assertTrue(contains(advent, LocalDate.parse("2026-11-29"), "advent-1/B"));
        assertTrue(contains(advent, LocalDate.parse("2027-01-03"), "epiphany/B"));
    }

    @Test
    void eachCelebrationAppearsOnce() {
        Set<String> keys = new HashSet<>();
        for (LiturgicalCalendar.Celebration day : LiturgicalCalendar.yearOf(LocalDate.parse("2026-11-08"))) {
            assertTrue(keys.add(day.key()), day.key());
            assertFalse(day.name().isBlank());
        }
        assertEquals("Domingo III da Páscoa", name(LiturgicalCalendar.yearOf(LocalDate.parse("2026-04-19")), "easter-3/A"));
    }

    private static boolean contains(List<LiturgicalCalendar.Celebration> days, LocalDate date, String key) {
        return days.stream().anyMatch(day -> day.date().equals(date) && day.key().equals(key));
    }

    private static String name(List<LiturgicalCalendar.Celebration> days, String key) {
        return days.stream().filter(day -> day.key().equals(key)).map(LiturgicalCalendar.Celebration::name).findFirst().orElse("");
    }
}
