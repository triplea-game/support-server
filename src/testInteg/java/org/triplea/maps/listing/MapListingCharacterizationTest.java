package org.triplea.maps.listing;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.database.rider.core.api.dataset.DataSet;
import com.github.database.rider.junit5.DBUnitExtension;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.triplea.http.client.ClientIdentifiers;
import org.triplea.http.client.maps.listing.MapDownloadItem;
import org.triplea.http.client.maps.listing.MapsClient;
import org.triplea.maps.IntegTestExtension;

/**
 * Characterization test for the maps listing HTTP endpoint. Each entry in the fixture mirrors real
 * production data (first 20 maps alphabetically as of April 2026), giving us a stable reference
 * point. If the server's response ever diverges from this baseline, the test will catch it.
 *
 * <p>The fixture {@code map_index_prod_sample.yml} was hand-crafted from
 * https://prod.triplea-game.org/support/maps/listing. When the fixture needs to be refreshed, fetch
 * the endpoint again, pick the first 20 maps, and update both the YAML and the expected list below.
 */
@DataSet(value = "map_index_prod_sample.yml", useSequenceFiltering = false)
@ExtendWith(IntegTestExtension.class)
@ExtendWith(DBUnitExtension.class)
class MapListingCharacterizationTest {

  private final MapsClient mapsClient;

  MapListingCharacterizationTest(final URI serverUri) {
    mapsClient =
        MapsClient.newClient(
            serverUri,
            ClientIdentifiers.builder()
                .applicationVersion("test")
                .systemId("test")
                .apiKey("")
                .build());
  }

  @Test
  void mapsListingMatchesProdSample() {
    final List<MapDownloadItem> results = mapsClient.fetchMapListing();

    assertThat(results)
        .hasSize(20)
        .containsExactlyInAnyOrder(
            // @formatter:off
            expectedMap(
                "1914-cow-empires",
                "https://github.com/triplea-maps/1914-cow-empires/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/1914-cow-empires/blob/master/preview.png?raw=true",
                4287712L,
                1631771026000L,
                "<br>Version 1.0, last update 2014.12.07 for engine 1.8.0.3\n<br>Game done by"
                    + " RogerCooper\n<br>Suggestions to RogerCoop@aol.com\n<br>\n<br>a.)"
                    + " Content:\n<br>1 Game\n<br>1914-COW-Empires\n<br>\n<br>b.) Rough"
                    + " overview:\n<br>A WW1 scenario using the Napoleonic Empires map and"
                    + " military/economic data from the Correlates of War database.\n<br>\n<br>c.)"
                    + " General tips:\n<br>Read game notes before playing.\n<br>\n"),
            expectedMap(
                "1939ARevised",
                "https://github.com/triplea-maps/1939ARevised/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/1939ARevised/blob/master/preview.png?raw=true",
                1301896L,
                1669561222000L,
                "No description available for: https://github.com/triplea-maps/1939ARevised."
                    + " Contact the map author and request they add a 'description.html' file"),
            expectedMap(
                "1939_Pact_of_Steel_Big_World",
                "https://github.com/triplea-maps/1939_Pact_of_Steel_Big_World/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/1939_Pact_of_Steel_Big_World/blob/master/preview.png?raw=true",
                803364L,
                1662315151000L,
                "1939 on the Big World Map with 7 powers"),
            expectedMap(
                "1941",
                "https://github.com/triplea-maps/1941/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/1941/blob/master/preview.png?raw=true",
                8553163L,
                1631771018000L,
                "No description available for: https://github.com/triplea-maps/1941. Contact the"
                    + " map author and request they add a 'description.html' file"),
            expectedMap(
                "1941_global_command_decision",
                "https://github.com/triplea-maps/1941_global_command_decision/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/1941_global_command_decision/blob/master/preview.png?raw=true",
                94976745L,
                1773475665000L,
                "<br>\n<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>1941"
                    + " GLOBAL COMMAND DECISION</b>\n<br>by Black Elk (graphics/testing) & TheDog"
                    + " (code/testing)\n<br>\n<br><b>Introduction</b>\n<br>It is late 1941; Germany"
                    + " has launched a massive invasion against the USSR and Japan is ready to strike"
                    + " at the USA in the Pacific.\n<br>\n<br><b>FEATURES</b>\n<br>\u2022 Massive map"
                    + " 16816x8085px=136Mpx, one of the biggest TripleA maps.  Unit size 54px high,"
                    + " this is with 4K screens in mind.\n<br>\u2022 Nearly 800 land & sea locations,"
                    + " with terrain effects, Desert, Forest, Marsh, Tundra, Urban, impassable"
                    + " mountains, canals\n<br>\n<br>\u2022 Stacking limit is enforced to help the"
                    + " AI.  Only 10 Air & 10 Sea units are allowed per Sea Zone/Territory.  Only 20"
                    + " Land per Territory.\n<br>\u2022 AI friendly, no Objectives, or"
                    + " Politics/Technology phases, both are scripted.  79 territories marked to"
                    + " guide the AI for better play.\n<br>\n<br>\u2022 Sea Zones are worth"
                    + " 1-2pu/turn and for Britain and Japan are vital for their"
                    + " survival\n<br>\u2022 All land territories bordering Sea Zones can be"
                    + " blockaded.  There are no convoy centres/route/zones.\n<br>\n<br>\u2022 Four"
                    + " Lend-Lease-Depots, 3 in USSR Archangelsk, Persian Corridor to Baku,"
                    + " Vladivostok  and Burma Road to Yunnan China\n<br>\u2022 Four types of HQ"
                    + " Commands, Air, Army, Fleet and Submarine, nations can have between none and"
                    + " five types of a HQ.  \n<br>&nbsp;&nbsp;&nbsp;&nbsp;These represent Rommel,"
                    + " Montgomery, Patton, Cunningham, Donitz, Nimitz, US 8th Air Force HQs etc."
                    + " and their staff.\n<br>\u2022 Four different type of \u201cFactory\u201d\n<br>\n<br>\u2022"
                    + " As a minimum requires TripleA 2.7.14848+\n<br>\n<br>For the latest download"
                    + " and discussion, copy and paste the link below\n<br>\n<br><a"
                    + " href=\"https://forums.triplea-game.org/topic/3326/1941-global-command-decision\">https://forums.triplea-game.org/topic/3326/1941-global-command-decision</a>\n<br>\n<br>"),
            expectedMap(
                "2013Conflict",
                "https://github.com/triplea-maps/2013Conflict/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/2013Conflict/blob/master/preview.png?raw=true",
                9717791L,
                1689426554000L,
                "Hypothetical World War 3 in 2013."),
            expectedMap(
                "2020",
                "https://github.com/triplea-maps/2020/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/2020/blob/master/preview.png?raw=true",
                2670316L,
                1634771469000L,
                "No description available for: https://github.com/triplea-maps/2020. Contact the"
                    + " map author and request they add a 'description.html' file"),
            expectedMap(
                "270BC_Tertiered",
                "https://github.com/triplea-maps/270BC_Variants/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/270BC_Variants/blob/master/preview.png?raw=true",
                8052661L,
                1645052831000L,
                "No description available for: https://github.com/triplea-maps/270BC_Variants."
                    + " Contact the map author and request they add a 'description.html' file"),
            expectedMap(
                "270bc",
                "https://github.com/triplea-maps/270bc/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/270bc/blob/master/preview.png?raw=true",
                8087243L,
                1631771025000L,
                "<br>By Doctor Che\n<br>Updated by Redrum\n<br>\n<br>Ancient Era Map of the"
                    + " Mediterranean\n<br>\n<br>Make yourself an empire around the Mediterranean"
                    + " Sea (the known world),\n<br>in the era when Hellenes, Romans, and"
                    + " Phoenicians ruled. Choose from a arsenal of\n<br>legionaires, hoplites,"
                    + " onagers, cataphracts, triremes, war elephants, and many more.\n<br>Territory"
                    + " names are based on cities at the time give or take 500 years.\n<br>\n"),
            expectedMap(
                "270bc_wars",
                "https://github.com/triplea-maps/270bc_wars/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/270bc_wars/blob/master/preview.png?raw=true",
                24085728L,
                1631771018000L,
                "<br>By Cernel.\n<br>Original 270BC map and game by Doctor Che.\n<br>Original 270BC"
                    + " game modified by Veqryn, Cernel, redrum.\n<br>Original 270BC map details"
                    + " and decorations, \"TripleA Ancient\" image, units images, territory names"
                    + " images, territory values images, dice images by Hepps.\n<br>\n<br>Mistress of"
                    + " Italy, Rome, looking over the sea, discovers herself engaged in the"
                    + " inexorable struggle for survival against the might of Carthage, encroaching"
                    + " on Africa and Spain, and the islands nearby, as far as the columns of"
                    + " Hercules, the end of the World.\n<br>In Greece, the royal hegemony of"
                    + " Macedonia is bitterly challenged by a warring coalition of free cities and"
                    + " leagues, from Sicily, in the west, to Asia, in the"
                    + " east.\n<br>Across what remains of what was taken by Alexander, what had"
                    + " begun as a spate of civil conflicts conflated into a dynastic strife between"
                    + " the new realms of Egypt and Syria.\n<br>Beyond the Hellenistic world, the"
                    + " ascendancy of Parthia looms on the horizon, eager to champion the"
                    + " resurgence of Persian supremacy to her former greatness.\n<br>Meanwhile,"
                    + " hailing from unknown lands, the tribes of Numidia are swarming out of the"
                    + " wilds of Libya, their savage want for slaves and violent desire for booty,"
                    + " unquenchable.\n<br>'Tis a clash of civilizations: for either side, the other"
                    + " side is to be eliminated.\n<br>\n<br>Special thanks to Navalland, guerrilla_J"
                    + " and Aposteles for play-testing.\n"),
            expectedMap(
                "300BC",
                "https://github.com/triplea-maps/300BC/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/300BC/blob/master/preview.png?raw=true",
                1903629L,
                1673105855000L,
                "The Mediterranean in 300BC."),
            expectedMap(
                "41_Oztea",
                "https://github.com/triplea-maps/41_Oztea_Variants/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/41_Oztea_Variants/blob/master/preview.png?raw=true",
                26833930L,
                1626574828000L,
                "No description available for: https://github.com/triplea-maps/41_Oztea_Variants."
                    + " Contact the map author and request they add a 'description.html' file"),
            expectedMap(
                "6Kingdoms",
                "https://github.com/triplea-maps/6Kingdoms/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/6Kingdoms/blob/master/preview.png?raw=true",
                2541514L,
                1701900576000L,
                "A war between two alliances of fantasy kingdoms."),
            expectedMap(
                "AA50-41-Maintenance",
                "https://github.com/triplea-maps/AA50-41-Maintenance/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/AA50-41-Maintenance/blob/master/preview.png?raw=true",
                9270272L,
                1707059237000L,
                "World War 2 Version 3 with Maintenance costs."),
            expectedMap(
                "AA50-BuildCaps",
                "https://github.com/triplea-maps/AA50-BuildCaps/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/AA50-BuildCaps/blob/master/preview.png?raw=true",
                9458131L,
                1699320131000L,
                "AA50/World World V3/Anniversary edition with production capped at what came in the"
                    + " box."),
            expectedMap(
                "AA50-Europe",
                "https://github.com/triplea-maps/AA50-Europe/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/AA50-Europe/blob/master/preview.png?raw=true",
                9611525L,
                1747346744000L,
                "World Word 2 in Europe on the AA50 map"),
            expectedMap(
                "AA50-realistic",
                "https://github.com/triplea-maps/AA50-realistic/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/AA50-realistic/blob/master/preview.png?raw=true",
                10492327L,
                1720105302000L,
                "World War II v3, 1942 with an modified setup."),
            expectedMap(
                "AAC-BuildCaps",
                "https://github.com/triplea-maps/AAC-BuildCaps/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/AAC-BuildCaps/blob/master/preview.png?raw=true",
                8788099L,
                1642552934000L,
                "No description available for: https://github.com/triplea-maps/AAC-BuildCaps."
                    + " Contact the map author and request they add a 'description.html' file"),
            expectedMap(
                "AAR-BuildCaps",
                "https://github.com/triplea-maps/AAR-BuildCaps/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/AAR-BuildCaps/blob/master/preview.png?raw=true",
                3026055L,
                1735505177000L,
                "World War II Revised with caps on how many units of each type that can be"
                    + " built.\n"),
            expectedMap(
                "AAZ",
                "https://github.com/triplea-maps/AAZ/archive/refs/heads/master.zip",
                "https://github.com/triplea-maps/AAZ/blob/master/preview.png?raw=true",
                1908091L,
                1720406059000L,
                "<b>Axis & Allies & Zombies</b>\n\t\t\t\t\t<br>\n\t\t\t\t\t<br>1. No strategic"
                    + " bombing.\n\t\t\t\t\t<br>2. No AA Guns / Mechanized Infantry /"
                    + " Cruisers.\n\t\t\t\t\t<br>3. No shore"
                    + " bombardment.\n\t\t\t\t\t<br>4. Cannot build new"
                    + " factories.\n\t\t\t\t\t<br>5. Recruitment centers are like factories but only"
                    + " build infantry\n\t\t\t\t\t<br>6  For balance, 3 Zombies start in"
                    + " Germany\n\t\t\t\t\t<br><b>Victory Condition:</b>\n\t\t\t\t\t<br> Take 1"
                    + " enemy capital and hold all your own\n\t\t\t\t\t<br> Zombies win if their"
                    + " income reaches 25\n            <br>Set Game/User Notifications/Show"
                    + " Trigger/Condition Change Roll Failure to"
                    + " off\n\t\t\t\t\t\t<br>Based upon Axis & Allies &"
                    + " Zombies\n\t\t\t\t\t\t<br>Player nations are not allowed to invade neutrals,"
                    + " but can move in once zombies have taken"
                    + " over.\n\t\t\t\t\t\t<br>Note that in this weIrd alternate history scenario,"
                    + " the Zombies are restless dead (Draugr) and not contagious as in most modern"
                    + " zombie fiction. They are capable of using weaponry, but too impaired to"
                    + " handle sophisticated weaponry or coordinate their"
                    + " actions.\n\t\t\t\t\t\t<br>The dead were awakened uncontrallably by Nazi"
                    + " occultists\n\t\t\t\t\t\t<br>The big change from the original game is that"
                    + " Zombies are not spawned by killing infantry, as there was no way to handle"
                    + " that in TripleA. Instead every area has 1% chance per turn of spawning a"
                    + " horde of 10. In addition each area has a 10% chance each turn of spawning"
                    + " 1.\n\t\t\t\t\t<br> Created for TripleA by Roger Cooper")
            // @formatter:on
            );
  }

  private static MapDownloadItem expectedMap(
      final String mapName,
      final String downloadUrl,
      final String previewImageUrl,
      final long downloadSizeInBytes,
      final long lastCommitDateEpochMilli,
      final String description) {
    return MapDownloadItem.builder()
        .mapName(mapName)
        .downloadUrl(downloadUrl)
        .previewImageUrl(previewImageUrl)
        .downloadSizeInBytes(downloadSizeInBytes)
        .lastCommitDateEpochMilli(lastCommitDateEpochMilli)
        .description(description)
        .build();
  }
}
