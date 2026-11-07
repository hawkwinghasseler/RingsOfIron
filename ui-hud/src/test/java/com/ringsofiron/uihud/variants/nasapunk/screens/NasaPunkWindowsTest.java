package com.ringsofiron.uihud.variants.nasapunk.screens;

import com.ringsofiron.uihud.data.AlertRegistry.Level;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.BoardingProvider;
import com.ringsofiron.uihud.providers.MissionProvider;
import com.ringsofiron.uihud.providers.TradeProvider;
import com.ringsofiron.uihud.providers.YardProvider;
import com.ringsofiron.uihud.variants.ClassicWindow;
import com.ringsofiron.uihud.variants.nasapunk.NpCanvas;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Nasa-punk windows' pages without a window: what each shows from the game's providers, and what its orders do. */
class NasaPunkWindowsTest {
    /** A screen that reads the boards again after each order and keeps what was said. */
    private static final class Host implements PageHost {
        final HudData data = new HudData();
        final Boards boards = new Boards();
        final List<String> said = new ArrayList<>();
        final List<ClassicWindow> opened = new ArrayList<>();
        Page page;
        boolean closed;

        Host(Page page) {
            this.page = page;
            page.attach(this);
        }

        void read() {
            boards.refresh(data);
            page.refresh(boards);
        }

        @Override
        public HudData data() {
            return data;
        }

        @Override
        public void say(String source, String text) {
            if (text != null && !text.isEmpty()) said.add(source + "|" + text);
        }

        @Override
        public void reread() {
            read();
        }

        @Override
        public boolean available(ClassicWindow window) {
            return true;
        }

        @Override
        public boolean open(ClassicWindow window) {
            opened.add(window);
            return true;
        }

        @Override
        public void closeScreen() {
            closed = true;
        }
    }

    /** A market of water and food, docked, with the loaders carrying what is bought. */
    private static final class Market implements TradeProvider {
        double credits = 5000, used = 0, capacity = 40, foodHeld = 0, foodOnOrder = 0;
        boolean hold = true;
        final List<String> calls = new ArrayList<>();

        @Override
        public void collect(TradeBoard out) {
            out.stationName = "Highport";
            out.credits = credits;
            out.cargoUsed = used;
            out.cargoCapacity = capacity;
            out.hasHold = hold;
            out.carried = true;
            TradeLine w = out.addLine();
            w.commodityId = "water";
            w.name = "Water";
            w.available = 936;
            w.buyPrice = 15;
            w.sellPrice = 13.6;
            w.stationBuys = true;
            TradeLine f = out.addLine();
            f.commodityId = "food";
            f.name = "Food";
            f.available = 1549;
            f.buyPrice = 50;
            f.sellPrice = 45.2;
            f.stationBuys = true;
            f.held = foodHeld;
            f.onOrder = foodOnOrder;
        }

        @Override
        public double buy(String commodityId, double units) {
            calls.add("buy " + commodityId + " " + (int) units);
            if (commodityId.equals("food")) foodOnOrder += units;
            return units;
        }

        @Override
        public double sell(String commodityId, double units) {
            calls.add("sell " + commodityId);
            return 0;
        }
    }

    @Test
    void theMarketBarsWhatCantBeTradedWithTheGamesReasonAndOrdersTheRest() {
        MarketPage page = new MarketPage();
        Host h = new Host(page);
        Market m = new Market();
        h.data.trade = m;
        h.read();
        assertTrue(page.offered(h.boards));
        Sect s = page.sects.first();
        assertEquals(2, s.rows.size);
        Row water = s.rows.get(0);
        assertEquals("WATER", water.cell(0).text);
        assertEquals("15.0", water.cell(2).text);
        assertEquals("BUY 1", water.acts.get(0).label);
        assertTrue(water.acts.get(0).live);
        assertFalse(water.acts.get(2).live, "nothing held to sell");
        assertEquals("NOTHING HELD TO SELL", water.acts.get(2).why);
        // Enter gives the first order that can be given on the cursor's row; the market's line says what came of it
        page.move(1);
        assertTrue(page.enter());
        assertEquals("buy food 1", m.calls.get(0));
        assertEquals("STATION|Ordered 1 Food: the loaders are carrying it aboard", h.said.get(0));
        assertEquals("+1", page.sects.first().rows.get(1).cell(5).text, "what is crossing the dock is on order");
        // a hold that is full bars the buys with the classic window's reason
        m.used = 40;
        h.read();
        Act buy = page.sects.first().rows.get(1).acts.get(0);
        assertFalse(buy.live);
        assertEquals("THE HOLD IS FULL", buy.why);
        assertFalse(page.press(buy));
        assertEquals("|BUY 1: THE HOLD IS FULL", h.said.get(h.said.size() - 1), "a barred order says why once pressed");
    }

    @Test
    void theCursorStaysOnItsRowWhenTheRowsMove() {
        MarketPage page = new MarketPage();
        Host h = new Host(page);
        Market m = new Market();
        h.data.trade = m;
        h.read();
        page.move(1);
        assertEquals("food", page.current().id);
        // the market lists food first now: the cursor follows it
        h.data.trade = new TradeProvider() {
            @Override
            public void collect(TradeBoard out) {
                m.collect(out);
                out.lines.reverse();
            }

            @Override
            public double buy(String commodityId, double units) {
                return 0;
            }

            @Override
            public double sell(String commodityId, double units) {
                return 0;
            }
        };
        h.read();
        assertEquals("food", page.current().id);
        assertEquals(0, page.sects.first().rows.indexOf(page.current(), true));
    }

    /** A board with one contract held. */
    private static final class Board implements MissionProvider {
        final List<Long> abandoned = new ArrayList<>();

        @Override
        public void collect(MissionBoard out) {
            Job j = out.addHeld();
            j.id = 7;
            j.title = "Deliver 40 Food to Shackleton Ice";
            j.terms = "500 cr on delivery, 5d 4h left";
        }

        @Override
        public String accept(long id) {
            return "";
        }

        @Override
        public String abandon(long id) {
            abandoned.add(id);
            return "Contract given up";
        }
    }

    @Test
    void aGuardedOrderIsGivenOnASecondPressWhileArmed() {
        HeldPage page = new HeldPage();
        Host h = new Host(page);
        Board b = new Board();
        h.data.missions = b;
        h.read();
        Act giveUp = page.current().acts.first();
        assertTrue(giveUp.guard);
        assertTrue(page.enter());
        assertTrue(b.abandoned.isEmpty(), "the first press only arms it");
        assertSame(Act.State.ARMED, page.current().acts.first().state);
        page.enter();
        assertEquals(1, b.abandoned.size());
        assertEquals("SPONSOR|Contract given up", h.said.get(0));
        // armed and left, it stands down after its time
        h.read();
        page.enter();
        page.act(Page.ARM_FOR + 0.1f);
        assertSame(Act.State.IDLE, page.current().acts.first().state);
        page.enter();
        assertEquals(1, b.abandoned.size(), "pressed again after the time ran out, it arms again");
    }

    /** A yard with three weapons on sale and a furnishing. */
    private static final class Yard implements YardProvider {
        double credits = 10000;
        int damaged;
        final List<String> calls = new ArrayList<>();

        @Override
        public void collect(YardBoard out) {
            out.stationName = "Highport";
            out.credits = credits;
            out.damaged = damaged;
            out.repairCost = 920;
            out.tanks = "Propellant 72%, oxygen 100%";
            out.refillCost = 260;
            Hardpoint h = out.addHardpoint();
            h.index = 0;
            h.name = "the nose";
            h.weapon = "Coilgun Mk1";
            for (String[] w : new String[][] {{"chain", "Chaingun Mk1", "9000"}, {"auto", "Autocannon Mk1", "18000"}, {"laser", "Laser Mk1", "6000"}}) {
                Offer o = out.addOffer();
                o.weaponId = w[0];
                o.name = w[1];
                o.price = Double.parseDouble(w[2]);
            }
            Furnishing f = out.addFurnishing();
            f.id = "dart";
            f.name = "Dartboard";
            f.use = "game";
            f.price = 120;
        }

        @Override
        public String orderRepairs() {
            calls.add("repairs");
            return "Repairs ordered";
        }

        @Override
        public String orderRefit(int hardpoint, String weaponId, boolean used) {
            calls.add("refit " + hardpoint + " " + weaponId + (used ? " used" : " new"));
            return "Refit ordered";
        }

        @Override
        public String placeFurnishing(String furnishingId) {
            calls.add("place " + furnishingId);
            return null;
        }
    }

    @Test
    void theYardStepsAMountThroughWhatIsOnSaleAndBarsWhatTheCreditsDontReach() {
        YardPage page = new YardPage();
        Host h = new Host(page);
        Yard y = new Yard();
        h.data.yard = y;
        h.read();
        Sect repairs = page.sects.get(0), tanks = page.sects.get(1), refits = page.sects.get(2);
        assertEquals("NOTHING NEEDS REPAIRING", repairs.sub);
        assertFalse(repairs.acts.first().live);
        assertEquals(2, tanks.rows.size);
        assertEquals("PROPELLANT", tanks.rows.first().cell(0).text);
        assertEquals("72%", tanks.rows.first().cell(1).text);
        assertEquals("FILL TANKS: 260 CR", tanks.sub);
        // the cursor on the nose: right steps its pick to the autocannon, which costs more than the credits
        Row nose = refits.rows.first();
        assertEquals("NOSE", nose.cell(0).text);
        assertEquals("‹ CHAINGUN MK1 ›", nose.cell(2).text);
        while (page.current() != nose) page.move(1);
        page.step(1);
        nose = page.sects.get(2).rows.first();
        assertEquals("‹ AUTOCANNON MK1 ›", nose.cell(2).text);
        assertFalse(nose.acts.get(0).live, "18 000 new is past 10 000");
        assertEquals("NOT ENOUGH CREDITS", nose.acts.get(0).why);
        assertTrue(nose.acts.get(1).live, "used, at half, it isn't");
        page.press(nose.acts.get(1));
        assertEquals("refit 0 auto used", y.calls.get(0));
        // placing a furnishing takes the player to the deck: the screen closes
        Row dart = page.sects.get(3).rows.first();
        page.press(dart.acts.first());
        assertEquals("place dart", y.calls.get(1));
        assertTrue(h.closed);
        // ships for sale go through the game's own window
        h.boards.shipyardOffered = true;
        page.refresh(h.boards);
        Act ships = page.acts.get(1);
        assertEquals("SHIPS FOR SALE", ships.label);
        page.press(ships);
        assertEquals(ClassicWindow.SHIPYARD, h.opened.get(0));
    }

    @Test
    void boardingLeavesOutWhatSellsForNothingAndListsTheRestByValue() {
        BoardingPage page = new BoardingPage();
        Host h = new Host(page);
        h.data.boarding = new BoardingProvider() {
            @Override
            public void collect(BoardingBoard out) {
                out.craftName = "Pirate raider 3";
                out.state = "taken: crew surrendered";
                out.salvage = out.taken = true;
                out.prisoners = 3;
                out.boardRefusal = "Already taken";
                part(out, "Escape pod", 0, true, "");
                part(out, "Helm", 400, true, "");
                part(out, "Reactor", 2000, true, "");
                part(out, "Engine", 1250, false, "inside the hull: latch on to strip it");
            }

            private void part(BoardingBoard out, String name, double value, boolean reach, String status) {
                Part p = out.addPart();
                p.name = name;
                p.value = value;
                p.toggleable = reach;
                p.status = status;
                p.x = out.parts.size;
            }

            @Override
            public String orderBoarding() {
                return "";
            }

            @Override
            public String recall() {
                return "No boarding under way";
            }

            @Override
            public String toggleStrip(int x, int y) {
                return "Marked";
            }

            @Override
            public String takeCargo(String commodity) {
                return "";
            }
        };
        h.read();
        Sect board = page.sects.get(0), salvage = page.sects.get(1);
        assertFalse(board.acts.get(0).live, "taken, nobody is sent");
        assertEquals("ALREADY TAKEN", board.acts.get(0).why);
        assertFalse(board.acts.get(2).live);
        assertEquals(3, salvage.rows.size, "the escape pod sells for nothing");
        assertEquals("REACTOR", salvage.rows.get(0).cell(0).text);
        assertEquals("ENGINE", salvage.rows.get(1).cell(0).text);
        assertEquals("HELM", salvage.rows.get(2).cell(0).text);
        Act engine = salvage.rows.get(1).acts.first();
        assertFalse(engine.live);
        assertEquals("INSIDE THE HULL: LATCH ON TO STRIP IT", engine.why, "a part out of reach says so before it is marked");
        assertEquals("3 PARTS · 3" + NpCanvas.THIN + "650 CR", salvage.sub);
    }

    @Test
    void theLogFiltersByItsSourceAndKeepsWhatIsMoreSerious() {
        LogPage page = new LogPage();
        Host h = new Host(page);
        h.data.messages.clear();
        h.data.messages.add("Engineering: Hull breach: HOLD", Level.WARNING, 1226);
        h.data.messages.add("Gunnery: Raider is disabled", Level.ADVISORY, 1610).important = true;
        h.data.messages.add("Helm: burn complete", Level.ADVISORY, 600);
        h.read();
        Sect log = page.sects.get(1);
        assertEquals(3, log.rows.size);
        assertEquals("DAY 0", log.rows.first().divider);
        assertEquals("00:20:26", log.rows.first().cell(0).text);
        assertEquals("ENGINEERING", log.rows.first().cell(1).text);
        assertEquals("Hull breach: HOLD", log.rows.first().cell(2).text);
        assertEquals(Row.WARNING, log.rows.first().mark);
        // the severity filter keeps the notable and more serious
        page.severity.at = 1;
        page.refresh(h.boards);
        assertEquals(2, page.sects.get(1).rows.size);
        assertEquals(2, page.severity.counts.get(1));
        // a source shows only its own notices
        page.severity.at = 0;
        page.source.set("HELM");
        page.refresh(h.boards);
        assertEquals(1, page.sects.get(1).rows.size);
        assertEquals("Burn complete", page.sects.get(1).rows.first().cell(2).text);
        assertEquals(1, page.source.counts.get(page.source.options.indexOf("HELM", false)));
        // a click steps a filter, and the page is built again
        Tables.click(page, page.severity, -2);
        assertEquals(1, page.severity.at);
    }

    @Test
    void theBoardsReadTheTanksTheBerthsAndTheDeliveriesThisMarketSupplies() {
        Host h = new Host(new MarketPage());
        h.data.trade = new Market();
        h.data.missions = new Board();
        h.data.yard = new Yard();
        h.data.crew = new com.ringsofiron.uihud.providers.CrewProvider() {
            @Override
            public void collect(CrewSheet out) {
                out.shipName = "Corvette";
                out.summary = "5 / 8 berths, 470 cr/wk in wages, 4 medical stores";
                Member m = out.addMember();
                m.name = "Cole";
                m.condition = "Light wound 100%";
            }

            @Override
            public String hire(String key) {
                return null;
            }

            @Override
            public String dismiss(long id) {
                return null;
            }

            @Override
            public String takePrisoners() {
                return null;
            }

            @Override
            public String settlePrisoner(long id) {
                return null;
            }

            @Override
            public String recruit(long id) {
                return null;
            }
        };
        h.data.navReadout.docked.present = true;
        h.data.navReadout.docked.name = "Highport";
        h.data.navReadout.docked.standing = 0f;
        h.read();
        Boards b = h.boards;
        assertEquals("HIGHPORT", b.station);
        assertEquals("+0", b.standing);
        assertEquals(2, b.tanks.size);
        assertEquals("OXYGEN", b.tanks.get(1));
        assertEquals(0.72f, b.tankFull.get(0), 1e-4);
        assertEquals(3, b.berthsFree());
        assertEquals(1, b.wounded);
        assertEquals(1, b.deliveries.size);
        Boards.Delivery d = b.deliveries.first();
        assertEquals(40, d.units);
        assertEquals("FOOD", d.commodity);
        assertEquals("SHACKLETON ICE", d.to);
        assertEquals("5D 4H LEFT", d.left);
    }

    @Test
    void columnsTooWideGiveUpWhatTheirWordsDontUseBeforeShrinkingAlike() {
        // the market's columns, 60 too wide: each gives from what its head and widest cell leave spare
        float[] w = {150f, 74f, 68f, 68f, 52f, 82f};
        float[] need = {125f, 60f, 50f, 56f, 40f, 72f};
        Tables.squeeze(w, need, 6, 60f);
        float sum = 0f;
        for (int c = 0; c < 6; c++) {
            assertTrue(w[c] >= need[c], "column " + c + " keeps its words whole");
            sum += w[c];
        }
        assertEquals(494f - 60f, sum, 1e-3);
        // past what is spare, all shrink alike
        float[] tight = {100f, 100f};
        Tables.squeeze(tight, new float[] {90f, 90f}, 2, 40f);
        assertEquals(80f, tight[0], 1e-3);
        assertEquals(80f, tight[1], 1e-3);
        // and keep 55 per cent whatever the room
        float[] one = {100f};
        Tables.squeeze(one, new float[] {100f}, 1, 80f);
        assertEquals(55f, one[0], 1e-3);
    }

    @Test
    void aColumnTooNarrowForItsWordsWidensIntoWhatTheOthersDontUse() {
        // the hiring hall: SKILLS needs 214 of its 190, and TERMS, taking what is left, uses 190 of 420
        float[] w = {130f, 96f, 190f, 420f};
        float[] need = {122f, 80f, 214f, 190f};
        Tables.fit(w, need, new boolean[] {true, true, true, false}, 4, 836f);
        assertEquals(214f, w[2], 1e-3);
        assertEquals(836f, w[0] + w[1] + w[2] + w[3], 1e-3);
        for (int c = 0; c < 4; c++) assertTrue(w[c] >= need[c], "column " + c + " keeps its words whole");
        assertTrue(w[3] > 390f, "the 24 come mostly from what TERMS doesn't use");
        // a column that takes what is left never widens for long words; it is cut instead
        float[] log = {92f, 132f, 500f};
        Tables.fit(log, new float[] {70f, 80f, 900f}, new boolean[] {true, true, false}, 3, 724f);
        assertEquals(500f, log[2], 1e-3);
    }

    @Test
    void aRowWithoutTermsOfItsOwnShowsItsCellsUnderTheirHeads() {
        Sect s = new Sect("FURNISHINGS");
        s.col(Col.start("PIECE", 190f)).col(Col.start("FOR", 110f)).col(Col.end("CR", 70f));
        Row r = s.row("couch");
        r.add("COUCH", Cell.Tone.NAME);
        r.add("LOUNGE", Cell.Tone.LABEL);
        r.add("900", Cell.Tone.VALUE);
        Terms terms = new Terms();
        com.badlogic.gdx.utils.Array<Line> t = terms.of(r, s);
        assertEquals(2, t.size, "the name heads the terms; the rest go under their heads");
        assertEquals("FOR", t.get(0).label);
        assertEquals("LOUNGE", t.get(0).value);
        assertEquals(Cell.Tone.VALUE, t.get(0).tone);
        assertEquals("900", t.get(1).value);
        // asked again, the same lines, not more
        assertEquals(2, terms.of(r, s).size);
        r.detail.add(Line.pair("PIECE", "COUCH", "", Cell.Tone.NAME));
        assertSame(r.detail, terms.of(r, s), "a row's own terms stand");
    }

    @Test
    void partsTooTallForTheirBoxGiveWayTallestFirst() {
        float[] h = {58f, 172f, 150f, 460f};
        Tables.cap(h, 4, 606f);
        assertEquals(58f, h[0], 1e-3);
        assertEquals(172f, h[1], 1e-3);
        assertEquals(150f, h[2], 1e-3);
        assertEquals(226f, h[3], 1e-3);
        float[] even = {300f, 300f, 300f};
        Tables.cap(even, 3, 600f);
        assertEquals(200f, even[0], 1e-3);
        assertEquals(200f, even[2], 1e-3);
    }

    @Test
    void theControlsLeaveOutTheSectionThatIsAllHere() {
        HelpPage page = new HelpPage();
        Host host = new Host(page);
        host.data.keyHints.add().key = "W/S";
        host.data.keyHints.hints.peek().label = "Thrust";
        host.data.hints = new com.ringsofiron.uihud.providers.KeyHintProvider() {
            @Override
            public void collect(KeyHintFrame out) {
            }

            @Override
            public void collectAll(HelpFrame out) {
                out.add("At the helm").add("W/S", "Thrust", "THRUST");
                HelpSection deck = out.add("On deck");
                deck.add("W/A/S/D", "Walk", "WALK");
                deck.add("W/S", "Thrust", "THRUST");
            }
        };
        host.read();
        assertEquals(2, page.sects.size);
        assertEquals("HERE", page.sects.get(0).title);
        assertEquals("ON DECK", page.sects.get(1).title);
        assertEquals("W/A/S/D", page.sects.get(1).rows.get(0).cells.get(0).text);
    }

    @Test
    void theWordsAreSetAsTheStyleGuideWritesThem() {
        String t = String.valueOf(NpCanvas.THIN);
        assertEquals("12" + t + "000 CR TO SIGN", Fmt.caps("12,000 cr to sign"));
        assertEquals("40 M³ OF FOOD", Fmt.caps("40 m3 of food"));
        assertEquals("1" + t + "549", Fmt.units(1549));
        assertEquals("15.0", Fmt.price(15));
        assertEquals("750", Fmt.price(750));
        assertEquals("−3", Fmt.signed(-3));
        assertEquals("+0", Fmt.signed(0));
        assertEquals("NOSE", Fmt.mount("the nose"));
        assertEquals("ENGINEERING", Sources.of("Engineering: Hull breach: HOLD"));
        assertEquals("Hull breach: HOLD", Sources.body("Engineering: Hull breach: HOLD"));
        assertEquals("LOG", Sources.of("Autosaved"));
    }
}
