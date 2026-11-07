package com.ringsofiron.uihud.variants.cassette.task;

import com.badlogic.gdx.graphics.Color;
import com.ringsofiron.uihud.data.HudData;
import com.ringsofiron.uihud.providers.UiContextProvider.Presence;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Controller;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Kind;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.Mount;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.State;
import com.ringsofiron.uihud.providers.WeaponStatusProvider.WeaponFrame;
import com.ringsofiron.uihud.variants.cassette.Yard;
import com.ringsofiron.uihud.variants.cassette.YardPaint;
import com.ringsofiron.uihud.variants.cassette.YardPart;
import com.ringsofiron.uihud.variants.cassette.YardText;

import java.util.Locale;

/**
 * Z7, the weapons fit (WPN) in gun-metal with rivets. Expanded at gunnery: a header for what is in
 * hand, then one row per mount (point defense included): its trigger lens, the mount engraved, the gun
 * on Dymo tape, who works it on flaps, a state lamp (or the barber flag when it isn't working) and the
 * state on flaps, then a value block built for the mount's kind; the decoy and the shields stand in
 * their own pod beside it ({@link DefencePod}), so the panel stays out of the clear zone. Compact at
 * the helm ({@link #compact}): one lamp per mount, missiles, decoy.
 */
final class GunPanel extends YardPart {
    static final float W = 368f, W_COMPACT = 192f, H_COMPACT = 62f;
    private static final float X = 700f, Y = 572f;
    private static final int MAX_ROWS = 8;
    private static final Color DIM = Yard.Finish.GUN.dim, INK = Yard.Finish.GUN.ink;

    private final TaskFrame f;
    boolean compact;
    /** The rows the layout has room for: the panel never grows into the clear zone. */
    int maxRows = MAX_ROWS;

    GunPanel(TaskFrame f) {
        super(X, Y);
        this.f = f;
    }

    private int rows() {
        return Math.min(Math.min(MAX_ROWS, maxRows), f.data.weaponFrame.mounts.size);
    }

    /** The height with {@code rows} mount rows, for the layout to find how many fit. */
    float heightFor(int rows) {
        return 42f + rows * 23f + 8f;
    }

    int mounts() {
        return f.data.weaponFrame.mounts.size;
    }

    /** The panel's height: a header and a row a mount. The decoy and the shields have their own pod at gunnery. */
    float height() {
        if (compact) return H_COMPACT;
        return heightFor(rows());
    }

    float width() {
        return compact ? W_COMPACT : W;
    }

    @Override
    protected void draw(YardPaint p) {
        if (compact) {
            drawCompact(p);
            return;
        }
        HudData d = f.data;
        WeaponFrame wf = d.weaponFrame;
        p.unit(X, Y, W, h, Yard.Finish.GUN, "b");
        boolean console = d.ctx.presence == Presence.WEAPONS, gun = d.ctx.presence == Presence.GUN;
        boolean pdAuto = false;
        for (Mount m : wf.mounts) if (m.controller == Controller.AUTO) pdAuto = true;
        p.legend("WEAPONS", 712f, 593f, INK);
        p.lamp(784f, 589f, 4f, Yard.WHITE_LAMP, console);
        p.legend("GUNS IN HAND", 792f, 593f, INK);
        p.lamp(884f, 589f, 4f, Yard.WHITE_LAMP, gun);
        p.legend("THIS GUN", 892f, 593f, INK);
        p.lamp(966f, 589f, 4f, Yard.WHITE_LAMP, pdAuto);
        p.legend("PD AUTO", 974f, 593f, INK);
        p.legend("MOUNT", 722f, 609f, DIM);
        p.legend("GUN", 782f, 609f, DIM);
        p.legend("BY", 843f, 609f, DIM);
        p.legend("STATE", 902f, 609f, DIM);
        int n = rows();
        if (wf.mounts.size > n) p.textR(com.ringsofiron.uihud.variants.cassette.YardFonts.Face.LEGEND, 12f, "+" + (wf.mounts.size - n) + " MORE", 1056f, 609f, DIM);
        for (int i = 0; i < n; i++) row(p, d, wf.mounts.get(i), 614f + i * 23f);
    }

    private void row(YardPaint p, HudData d, Mount m, float yy) {
        boolean auto = m.controller == Controller.AUTO;
        if (!auto) p.push(708f, yy + 4f, 9f, 9f, new String[]{""}, Yard.WHITE_LAMP, m.controller == Controller.PLAYER, YardPaint.Kind.INDICATOR, false, false);
        // The mount's name, engraved smaller when long ("FWD PORT PD") so the gun's tape never covers it
        p.legendFit(YardText.fit(m.name, 12), 722f, yy + 13f, 58f, INK);
        p.dymo(782f, yy + 1f, gunTape(m.weapon));
        String by = by(m.controller);
        p.flaps(843f, yy, by, 6, 6.8f, 17f, 12.5f, by.equals("HAND") ? Yard.FLAP_AMBER : Yard.FLAP_TEXT);
        String state = state(m, d);
        Yard.Lamp lamp = lamp(m, state);
        if (!m.working() && m.state != State.EMPTY) p.barberTab(889f, yy + 1.5f, 9f, 14f);
        else p.lamp(893.5f, yy + 8.5f, 4f, lamp == null ? Yard.WHITE_LAMP : lamp, lamp != null);
        p.flaps(902f, yy, YardText.fit(state, 12), 12, 6.8f, 17f, 12.5f, Yard.FLAP_TEXT);
        float vx = 988f;
        if (m.kind == Kind.LAUNCHER && m.magazine > 0) {
            p.drums(vx, yy, String.valueOf(Math.min(9, Math.max(0, m.ammo))), 11f, 17f, 15f, false);
            p.legend("/", vx + 13f, yy + 13f, INK);
            p.drums(vx + 20f, yy, String.valueOf(Math.min(9, m.magazine)), 11f, 17f, 15f, false);
            boolean lock = d.targetStatus.present && m.inArc;
            p.push(1026f, yy + 1f, 32f, 15f, new String[]{"LOCK"}, Yard.WHITE_LAMP, lock, YardPaint.Kind.INDICATOR, false, false);
        } else if (m.state == State.RELOADING && m.cooldown > 0f) {
            value(p, vx, yy, "RLD", String.format(Locale.ROOT, "%.1f", Math.min(9.9f, m.cooldown)), "S");
        } else if (m.working() && !Float.isNaN(m.hitChance) && m.kind != Kind.POINT_DEFENSE) {
            value(p, vx, yy, "HIT", String.valueOf(Math.min(99, Math.round(m.hitChance * 100f))), "%");
        }
    }

    /** A value block: the quantity's name on flaps, its number on drums ending at a fixed edge, its unit. */
    private static void value(YardPaint p, float vx, float yy, String label, String v, String unit) {
        p.flaps(vx, yy, label, 3, 6.8f, 17f, 12.5f, Yard.FLAP_TEXT);
        p.drums(1043f - YardPaint.drumWidth(v, 11f), yy, v, 11f, 17f, 15f, false);
        p.flaps(1047f, yy, unit, 1, 6.8f, 17f, 12.5f, Yard.FLAP_TEXT);
    }

    /** The gun's name punched on Dymo: names a refit changes, short as a label maker makes them. */
    static String gunTape(String weapon) {
        String u = weapon.toUpperCase(Locale.ROOT).replace("-", "");
        if (u.startsWith("AUTOCANNON")) return "AUTOCAN";
        if (u.contains(" ")) u = u.substring(0, u.indexOf(' '));
        return u.length() > 8 ? u.substring(0, 8) : u;
    }

    static String by(Controller c) {
        switch (c) {
            case PLAYER: return "HAND";
            case CREW: return "GUNNER";
            case CONSOLE: return "CONSOL";
            case AUTO: return "AUTO";
            default: return "TRIG";
        }
    }

    /** The state on flaps, as the spec words it. */
    static String state(Mount m, HudData d) {
        switch (m.state) {
            case DESTROYED: return "DAMAGED";
            case REFIT: return "REFIT";
            case SHORTED: return "SHORTED";
            case STUNNED: return "STUNNED";
            case NO_POWER: return "NO POWER";
            case EMPTY: return "EMPTY";
            case RELOADING: return "RELOAD";
            case CHARGING: return "CHARGING";
            default: break;
        }
        if (d.ctx.docked || d.weaponFrame.holdFire) return "HOLD";
        if (m.kind == Kind.POINT_DEFENSE) return m.engaging || m.defending ? "ENGAGING" : "IDLE";
        if (!d.targetStatus.present) return "NO TARGET";
        if (!m.inArc) return "OUT OF ARC";
        return "READY";
    }

    /** Green when it would fire now, amber while it waits, dark when idle; null for dark. */
    static Yard.Lamp lamp(Mount m, String state) {
        switch (state) {
            case "READY": case "ENGAGING": return Yard.GREEN_LAMP;
            case "IDLE": return null;
            default: return Yard.AMBER_LAMP;
        }
    }

    // --- Compact, at the helm ------------------------------------------------------------------

    private void drawCompact(YardPaint p) {
        HudData d = f.data;
        WeaponFrame wf = d.weaponFrame;
        float x = X, y = Y;
        p.unit(x, y, W_COMPACT, h, Yard.Finish.GUN, "b");
        p.legend("WEAPONS", x + 12f, y + 21f, INK);
        int pips = 0, ammo = 0, mag = 0;
        boolean launcherReady = false;
        for (Mount m : wf.mounts) {
            if (m.kind == Kind.LAUNCHER && m.magazine > 0) {
                ammo += Math.max(0, m.ammo);
                mag += m.magazine;
                launcherReady |= m.state == State.READY;
            }
            if (pips >= 5) continue;
            float mx = x + 82f + pips * 22f;
            String state = state(m, d);
            Yard.Lamp lamp = lamp(m, state);
            if (!m.working() && m.state != State.EMPTY) p.barberTab(mx - 5f, y + 10f, 10f, 13f);
            else p.lamp(mx, y + 16f, 4.5f, lamp == null ? Yard.WHITE_LAMP : lamp, lamp != null);
            pips++;
        }
        p.legend("MSL", x + 12f, y + 46f, INK);
        p.drums(x + 40f, y + 32.5f, String.valueOf(Math.min(9, ammo)), 11f, 17f, 15f, false);
        p.legend("OF", x + 56f, y + 46f, INK);
        p.drums(x + 74f, y + 32.5f, String.valueOf(Math.min(9, mag)), 11f, 17f, 15f, false);
        p.lamp(x + 98f, y + 41f, 4f, Yard.GREEN_LAMP, launcherReady && ammo > 0);
        boolean ready = wf.decoyCooldown <= 0f;
        p.legend("DECOY", x + 112f, y + 46f, INK);
        p.lamp(x + 160f, y + 41f, 4f, ready ? Yard.GREEN_LAMP : Yard.AMBER_LAMP, true);
        if (!ready) p.drums(x + 168f, y + 32.5f, String.valueOf(Math.min(9, (int) Math.ceil(wf.decoyCooldown))), 11f, 17f, 15f, false);
    }

    @Override
    public String hover(float px, float py) {
        if (compact) return null;
        float ly = localY(py);
        WeaponFrame wf = f.data.weaponFrame;
        int i = (int) ((ly - 614f) / 23f);
        if (ly >= 614f && i >= 0 && i < rows()) {
            Mount m = wf.mounts.get(i);
            return m.name + ": " + m.weapon + ", " + by(m.controller).toLowerCase(Locale.ROOT) + ", " + state(m, f.data).toLowerCase(Locale.ROOT);
        }
        return null;
    }
}
