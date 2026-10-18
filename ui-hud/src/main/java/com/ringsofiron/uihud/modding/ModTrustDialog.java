package com.ringsofiron.uihud.modding;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.ringsofiron.moddingdata.ModManifest;
import com.ringsofiron.moddingdata.ModTrustPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Asks the player before mod scripts run with full access to their computer.
 *
 * Mod loading can happen on either thread:
 * <ul>
 *   <li>On a <b>background thread</b> (recommended), {@link #allowScripts} shows the dialog on the
 *       render thread and waits for the answer.</li>
 *   <li>On the <b>render thread</b> it cannot wait without freezing the game, so it shows the
 *       dialog, returns false (data and assets load, scripts do not), and calls the
 *       {@code onDecision} listener when the player answers so the game can reload mods.</li>
 * </ul>
 * With "Remember" ticked, the answer is stored per exact set of mods and versions, so the
 * dialog only reappears when a script mod is added or updated.
 */
public class ModTrustDialog implements ModTrustPolicy {
    private static final String PREF_PREFIX = "modTrust.";

    private final Stage stage;
    private final Skin skin;
    private final Preferences prefs;
    private final Thread renderThread;
    private Consumer<Boolean> onDecision;

    /**
     * Create on the render thread.
     *
     * @param prefs where remembered answers are kept; null keeps nothing between runs
     */
    public ModTrustDialog(Stage stage, Skin skin, Preferences prefs) {
        this.stage = stage;
        this.skin = skin;
        this.prefs = prefs;
        this.renderThread = Thread.currentThread();
    }

    /** Called with the player's answer whenever the dialog is answered. */
    public ModTrustDialog onDecision(Consumer<Boolean> listener) {
        this.onDecision = listener;
        return this;
    }

    @Override
    public boolean allowScripts(List<ModManifest> scriptMods) {
        if (scriptMods.isEmpty()) return true;
        String key = rememberKey(scriptMods);
        if (prefs != null && prefs.contains(key)) return prefs.getBoolean(key);

        if (Thread.currentThread() == renderThread) {
            show(scriptMods, key, null);
            return false;
        }
        boolean[] answer = new boolean[1];
        CountDownLatch latch = new CountDownLatch(1);
        Gdx.app.postRunnable(() -> show(scriptMods, key, allowed -> {
            answer[0] = allowed;
            latch.countDown();
        }));
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return answer[0];
    }

    private void show(List<ModManifest> mods, String key, Consumer<Boolean> waiter) {
        CheckBox remember = new CheckBox(" Remember for these mods", skin);
        Dialog dialog = new Dialog("MOD SCRIPTS", skin) {
            @Override
            protected void result(Object object) {
                boolean allowed = Boolean.TRUE.equals(object);
                if (remember.isChecked() && prefs != null) {
                    prefs.putBoolean(key, allowed);
                    prefs.flush();
                }
                if (waiter != null) waiter.accept(allowed);
                if (onDecision != null) onDecision.accept(allowed);
            }
        };
        dialog.pad(30f, 16f, 16f, 16f);
        Table body = dialog.getContentTable();
        body.defaults().left();
        Label intro = new Label("These mods include scripts. Scripts run with full access to your\n"
                + "computer, like any program you install. Only run mods from people you trust.", skin);
        body.add(intro).padBottom(10f).row();
        for (ModManifest m : mods) {
            String author = m.author == null || m.author.isEmpty() ? "" : "  by " + m.author;
            body.add(new Label("- " + displayName(m) + " " + m.version + author, skin, "warn")).row();
        }
        body.add(new Label("Without scripts, these mods' data and assets still load.", skin, "dim")).padTop(10f).row();
        body.add(remember).padTop(6f).row();
        dialog.getButtonTable().defaults().minWidth(120f).pad(6f, 6f, 0f, 6f);
        dialog.button("Run scripts", Boolean.TRUE);
        dialog.button("Data only", Boolean.FALSE);
        dialog.key(com.badlogic.gdx.Input.Keys.ESCAPE, Boolean.FALSE);
        dialog.show(stage);
    }

    private static String displayName(ModManifest m) {
        return m.name != null && !m.name.isEmpty() ? m.name : m.id;
    }

    /** Preference key for one exact set of script mods and their versions, order-independent. */
    static String rememberKey(List<ModManifest> mods) {
        List<String> parts = new ArrayList<>();
        for (ModManifest m : mods) parts.add(m.id + "@" + m.version);
        parts.sort(null);
        return PREF_PREFIX + String.join(",", parts);
    }
}
