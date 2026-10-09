package app.mise.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ListParserTest {
    private val columbia = SavedPlace(
        id = 1, name = "Cafe Mezcal", city = "Columbia, MD", googleName = "Café Mezcla",
        address = "8775 Centre Park Dr, Columbia, MD 21045, USA",
    )
    private val elliottCityPanera = SavedPlace(
        id = 2, name = "Panera Bread", city = "Ellicott City, MD", address = "1 Main St, Ellicott City, MD 21043, USA",
    )

    @Test fun sameNameInADifferentCityIsNotADuplicate() {
        assertNull(ListParser.possibleDuplicate("Panera Bread", "Columbia, MD", listOf(elliottCityPanera)))
    }

    @Test fun sameNameInTheSameCityIsADuplicate() {
        assertNotNull(ListParser.possibleDuplicate("Panera Bread", "Ellicott City, MD", listOf(elliottCityPanera)))
    }

    @Test fun cityMatchesAgainstTheGoogleAddressToo() {
        // saved with a different city text, but Google's address says Columbia, MD
        val p = columbia.copy(city = "Howard County")
        assertNotNull(ListParser.possibleDuplicate("Cafe Mezcal", "Columbia, MD", listOf(p)))
    }

    @Test fun matchesGoogleNameAsWellAsTypedName() {
        assertNotNull(ListParser.possibleDuplicate("Café Mezcla", "Columbia, MD", listOf(columbia)))
    }

    @Test fun entryWithNoLocationIsComparedByNameOnly() {
        assertNotNull(ListParser.possibleDuplicate("Cafe Mezcal", "", listOf(columbia)))
    }

    @Test fun unrelatedNamesDoNotMatch() {
        assertNull(ListParser.possibleDuplicate("Akira Ramen", "Columbia, MD", listOf(columbia)))
    }

    @Test fun parsesHeadersAndParentheticals() {
        val parsed = ListParser.parse(
            "-- Columbia, MD --\nRoggenart (Brunch)\nBusboys and Poets (Get Crab Fritters)\n\n-- Turf Valley, MD --\nFacci\nThe Crazy Mason - Sugar Haven (Must go with Sophia)",
            "",
        )
        assertEquals(listOf("Roggenart", "Busboys and Poets", "Facci", "The Crazy Mason - Sugar Haven"), parsed.map { it.name })
        assertEquals(listOf("Columbia, MD", "Columbia, MD", "Turf Valley, MD", "Turf Valley, MD"), parsed.map { it.location })
        assertEquals(listOf(EntryKind.Cuisine, EntryKind.Dish, EntryKind.Cuisine, EntryKind.Note), parsed.map { it.kind })
        assertEquals("Crab Fritters", parsed[1].text)
    }
}
