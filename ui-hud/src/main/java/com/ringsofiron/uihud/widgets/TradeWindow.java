package com.ringsofiron.uihud.widgets;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.ringsofiron.uihud.providers.TradeProvider;
import com.ringsofiron.uihud.providers.TradeProvider.TradeBoard;
import com.ringsofiron.uihud.providers.TradeProvider.TradeLine;

import java.util.Locale;

/**
 * The station market while the ship is docked: credits and hold, then one row per commodity with
 * stock, prices, what you carry, and buy/sell buttons. Refreshes from the {@link TradeProvider} a few
 * times a second; closes itself when the ship leaves trading range. A dock hint label, owned by the
 * caller, tells the player trading is available while the window is closed.
 */
public class TradeWindow extends Window {
    public static final float REFRESH_INTERVAL = 0.5f;
    /** Units the large buy/sell buttons trade. */
    public static final int BULK = 10;

    private final Skin skin;
    private final Label dockHint;
    private final TradeBoard board = new TradeBoard();
    private final Label summary;
    private final Label status;
    private final Table rows = new Table();
    private final Array<Row> rowWidgets = new Array<>();
    private TradeProvider provider;
    private float sinceRefresh = REFRESH_INTERVAL;

    private final class Row {
        final String commodityId;
        final Label stock, buyPrice, sellPrice, held;
        final TextButton buy1, buyBulk, sell1, sellAll;

        Row(TradeLine line) {
            commodityId = line.commodityId;
            stock = new Label("", skin);
            buyPrice = new Label("", skin);
            sellPrice = new Label("", skin);
            held = new Label("", skin);
            buy1 = button("Buy 1", () -> trade(true, 1));
            buyBulk = button("Buy " + BULK, () -> trade(true, BULK));
            sell1 = button("Sell 1", () -> trade(false, 1));
            sellAll = button("Sell all", () -> trade(false, Double.MAX_VALUE));
            rows.add(new Label(line.name, skin)).left().padRight(14f);
            rows.add(stock).right().padRight(14f);
            rows.add(buyPrice).right().padRight(14f);
            rows.add(sellPrice).right().padRight(14f);
            rows.add(held).right().padRight(14f);
            rows.add(buy1).padRight(3f);
            rows.add(buyBulk).padRight(10f);
            rows.add(sell1).padRight(3f);
            rows.add(sellAll).row();
        }

        void update(TradeLine line) {
            stock.setText(formatUnits(line.available));
            buyPrice.setText(line.available > 0 ? formatPrice(line.buyPrice) : "-");
            sellPrice.setText(line.stationBuys ? formatPrice(line.sellPrice) : "-");
            held.setText(formatUnits(line.held));
            buy1.setDisabled(!canBuy(board, line));
            buyBulk.setDisabled(!canBuy(board, line));
            sell1.setDisabled(!canSell(line));
            sellAll.setDisabled(!canSell(line));
        }

        private void trade(boolean buying, double units) {
            if (provider == null) return;
            TradeLine line = find(commodityId);
            if (line == null) return;
            String name = line.name;
            double credits = board.credits;
            double done = buying ? provider.buy(commodityId, units) : provider.sell(commodityId, units);
            refresh();
            if (done <= 0) {
                // Refresh reuses line objects, so look this commodity up again.
                TradeLine now = find(commodityId);
                status.setText(buying ? "Can't buy " + name + ": " + (now == null ? "it is no longer listed" : buyRefusal(board, now))
                        : "The station won't take " + name);
                status.setStyle(skin.get("warn", Label.LabelStyle.class));
                return;
            }
            double credit = Math.abs(board.credits - credits);
            status.setText(String.format(Locale.ROOT, "%s %s %s for %s", buying ? "Bought" : "Sold",
                    formatUnits(done), name, formatCredits(credit)));
            status.setStyle(skin.get("default", Label.LabelStyle.class));
        }
    }

    public TradeWindow(Skin skin, Label dockHint) {
        super("", skin);
        this.skin = skin;
        this.dockHint = dockHint;
        setModal(false);
        setMovable(true);
        pad(28f, 12f, 12f, 12f);
        getTitleTable().padLeft(8f);
        summary = new Label("", skin);
        status = new Label("", skin, "dim");
        add(summary).left().row();
        add(rows).left().padTop(8f).row();
        add(status).left().padTop(8f).row();
        TextButton close = button("Close [T]", () -> setOpen(false));
        add(close).right().padTop(10f);
        setVisible(false);
        dockHint.setVisible(false);
    }

    public void setProvider(TradeProvider provider) {
        this.provider = provider;
        refresh();
    }

    public boolean isOpen() {
        return isVisible();
    }

    /** True while the ship is in trading range of a station. */
    public boolean isDocked() {
        return board.isDocked();
    }

    /** Opens the market if docked; closing always works. Returns whether the window is now open. */
    public boolean setOpen(boolean open) {
        if (open) {
            refresh();
            if (!board.isDocked()) return false;
            status.setText("");
            setVisible(true);
            pack();
            if (getStage() != null) {
                setPosition((getStage().getWidth() - getWidth()) / 2f, (getStage().getHeight() - getHeight()) / 2f);
            }
            toFront();
        } else {
            setVisible(false);
        }
        dockHint.setVisible(board.isDocked() && !isVisible());
        return isVisible();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        sinceRefresh += delta;
        if (sinceRefresh >= REFRESH_INTERVAL) refresh();
    }

    /** Pulls the board from the provider and updates the hint, the summary and every row. */
    public void refresh() {
        sinceRefresh = 0;
        board.clear();
        if (provider != null) provider.collect(board);
        if (!board.isDocked() && isVisible()) setVisible(false);
        dockHint.setVisible(board.isDocked() && !isVisible());
        if (!board.isDocked()) return;
        dockHint.setText(board.hasHold ? "Docked at " + board.stationName + "   [T] Trade"
                : "Docked at " + board.stationName + " (no cargo hold)");
        getTitleLabel().setText(board.stationName.toUpperCase(Locale.ROOT) + " MARKET");
        summary.setText(String.format(Locale.ROOT, "Credits %s     Hold %s / %s m3", formatCredits(board.credits),
                formatUnits(board.cargoUsed), formatUnits(board.cargoCapacity)));
        if (!sameCommodities()) rebuildRows();
        for (int i = 0; i < rowWidgets.size; i++) rowWidgets.get(i).update(board.lines.get(i));
    }

    private boolean sameCommodities() {
        if (rowWidgets.size != board.lines.size) return false;
        for (int i = 0; i < rowWidgets.size; i++) {
            if (!rowWidgets.get(i).commodityId.equals(board.lines.get(i).commodityId)) return false;
        }
        return true;
    }

    private void rebuildRows() {
        rows.clearChildren();
        rowWidgets.clear();
        rows.add(new Label("Commodity", skin, "dim")).left().padRight(14f);
        rows.add(new Label("Stock", skin, "dim")).right().padRight(14f);
        rows.add(new Label("Buy at", skin, "dim")).right().padRight(14f);
        rows.add(new Label("Sell at", skin, "dim")).right().padRight(14f);
        rows.add(new Label("Hold", skin, "dim")).right().padRight(14f);
        rows.add().colspan(4).row();
        for (TradeLine line : board.lines) rowWidgets.add(new Row(line));
        if (isVisible()) pack();
    }

    private TradeLine find(String commodityId) {
        for (TradeLine l : board.lines) if (l.commodityId.equals(commodityId)) return l;
        return null;
    }

    private TextButton button(String text, Runnable action) {
        TextButton b = new TextButton(text, skin);
        b.pad(2f, 8f, 2f, 8f);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!b.isDisabled()) action.run();
            }
        });
        return b;
    }

    public TradeBoard getBoard() {
        return board;
    }

    /** The station has stock, the hold has room and the player has credits (the market sells what they afford). */
    static boolean canBuy(TradeBoard board, TradeLine line) {
        return board.hasHold && line.available > 1e-6 && board.freeCargo() > 1e-6 && board.credits > 1e-6;
    }

    /**
     * Why a buy filled nothing, given the board as it was before the attempt. With volume left over,
     * the hold had no bay free for this commodity: each cargo-bay tile carries a single commodity.
     */
    static String buyRefusal(TradeBoard board, TradeLine line) {
        if (!board.hasHold) return "this ship has no cargo hold";
        if (line.available <= 1e-6) return "the station has none to sell";
        if (board.credits <= 1e-6) return "no credits";
        if (board.freeCargo() <= 1e-6) return "the hold is full";
        return "no free cargo bay for it (each bay holds one commodity)";
    }

    static boolean canSell(TradeLine line) {
        return line.stationBuys && line.held > 1e-6;
    }

    static String formatCredits(double credits) {
        return String.format(Locale.ROOT, "%,.0f cr", credits);
    }

    static String formatPrice(double price) {
        return price >= 100 ? String.format(Locale.ROOT, "%,.0f", price) : String.format(Locale.ROOT, "%.1f", price);
    }

    static String formatUnits(double units) {
        double r = Math.rint(units);
        return Math.abs(units - r) < 0.05 ? String.format(Locale.ROOT, "%,.0f", r) : String.format(Locale.ROOT, "%.1f", units);
    }
}
