package com.hawkwing.RingsOfIron.shell;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.input.NativeInputConfiguration;
import com.badlogic.gdx.utils.IntSet;

/**
 * The backend's {@link Input} seen through the player's {@link KeyBindings} and gamepad. The game
 * keeps asking for its default keys; this answers with the key the player bound to each, or a
 * gamepad holding it. Key events reach the game's input processor through {@link #processor()},
 * translated the same way, and the pad's presses arrive as key events too.
 *
 * <p>The game shell puts this in {@code Gdx.input} at the start of every frame (the LWJGL3 backend
 * resets {@code Gdx.input} before each frame). While {@link #setRemapping remapping} is off, as in
 * the ship editor, keys pass through as they are.
 */
public class RemappedInput implements Input {
    private Input real;
    private final KeyBindings bindings;
    private final GamepadKeys pad;
    private final IntSet edges = new IntSet();
    private InputProcessor inner;
    /** Sees the pad's key events before the game, as the shell's own input does the keyboard's. */
    private InputProcessor front;
    private boolean remapping = true;

    private final InputProcessor translator = new InputProcessor() {
        @Override public boolean keyDown(int keycode) { return inner != null && inner.keyDown(map(keycode)); }
        @Override public boolean keyUp(int keycode) { return inner != null && inner.keyUp(map(keycode)); }
        @Override public boolean keyTyped(char character) { return inner != null && inner.keyTyped(character); }
        @Override public boolean touchDown(int x, int y, int pointer, int button) { return inner != null && inner.touchDown(x, y, pointer, button); }
        @Override public boolean touchUp(int x, int y, int pointer, int button) { return inner != null && inner.touchUp(x, y, pointer, button); }
        @Override public boolean touchCancelled(int x, int y, int pointer, int button) { return inner != null && inner.touchCancelled(x, y, pointer, button); }
        @Override public boolean touchDragged(int x, int y, int pointer) { return inner != null && inner.touchDragged(x, y, pointer); }
        @Override public boolean mouseMoved(int x, int y) { return inner != null && inner.mouseMoved(x, y); }
        @Override public boolean scrolled(float amountX, float amountY) { return inner != null && inner.scrolled(amountX, amountY); }
    };

    public RemappedInput(Input real, KeyBindings bindings, GamepadKeys pad) {
        this.real = real;
        this.bindings = bindings;
        this.pad = pad;
    }

    /** The backend's own input; call each frame with {@code Gdx.input} before putting this in its place. */
    public void setReal(Input real) {
        if (real != this) this.real = real;
    }

    public Input getReal() {
        return real;
    }

    public void setRemapping(boolean remapping) {
        this.remapping = remapping;
    }

    public void setFront(InputProcessor front) {
        this.front = front;
    }

    /** The processor to give the backend: it hands the game's processor its own keys. */
    public InputProcessor processor() {
        return translator;
    }

    /** Reads the pad and sends its presses and releases as key events. Call once a frame. */
    public void update() {
        pad.poll();
        pad.pressedNow(edges);
        IntSet.IntSetIterator it = edges.iterator();
        while (it.hasNext) {
            int k = it.next();
            if ((front == null || !front.keyDown(k)) && inner != null) inner.keyDown(k);
        }
        pad.releasedNow(edges);
        it = edges.iterator();
        while (it.hasNext) {
            int k = it.next();
            if ((front == null || !front.keyUp(k)) && inner != null) inner.keyUp(k);
        }
    }

    /** The game's key a physical key plays. */
    private int map(int physical) {
        return remapping ? bindings.logical(physical) : physical;
    }

    private int physical(int key) {
        return remapping ? bindings.physical(key) : key;
    }

    @Override
    public boolean isKeyPressed(int key) {
        if (key == Keys.ANY_KEY) return real.isKeyPressed(key) || pad.any();
        return real.isKeyPressed(physical(key)) || pad.pressed(key);
    }

    @Override
    public boolean isKeyJustPressed(int key) {
        if (key == Keys.ANY_KEY) return real.isKeyJustPressed(key);
        return real.isKeyJustPressed(physical(key)) || pad.justPressed(key);
    }

    @Override
    public void setInputProcessor(InputProcessor processor) {
        inner = processor;
    }

    @Override
    public InputProcessor getInputProcessor() {
        return inner;
    }

    // Everything else is the backend's.
    @Override public float getAccelerometerX() { return real.getAccelerometerX(); }
    @Override public float getAccelerometerY() { return real.getAccelerometerY(); }
    @Override public float getAccelerometerZ() { return real.getAccelerometerZ(); }
    @Override public float getGyroscopeX() { return real.getGyroscopeX(); }
    @Override public float getGyroscopeY() { return real.getGyroscopeY(); }
    @Override public float getGyroscopeZ() { return real.getGyroscopeZ(); }
    @Override public int getMaxPointers() { return real.getMaxPointers(); }
    @Override public int getX() { return real.getX(); }
    @Override public int getX(int pointer) { return real.getX(pointer); }
    @Override public int getDeltaX() { return real.getDeltaX(); }
    @Override public int getDeltaX(int pointer) { return real.getDeltaX(pointer); }
    @Override public int getY() { return real.getY(); }
    @Override public int getY(int pointer) { return real.getY(pointer); }
    @Override public int getDeltaY() { return real.getDeltaY(); }
    @Override public int getDeltaY(int pointer) { return real.getDeltaY(pointer); }
    @Override public boolean isTouched() { return real.isTouched(); }
    @Override public boolean justTouched() { return real.justTouched(); }
    @Override public boolean isTouched(int pointer) { return real.isTouched(pointer); }
    @Override public float getPressure() { return real.getPressure(); }
    @Override public float getPressure(int pointer) { return real.getPressure(pointer); }
    @Override public boolean isButtonPressed(int button) { return real.isButtonPressed(button); }
    @Override public boolean isButtonJustPressed(int button) { return real.isButtonJustPressed(button); }
    @Override public void getTextInput(TextInputListener l, String title, String text, String hint) { real.getTextInput(l, title, text, hint); }
    @Override public void getTextInput(TextInputListener l, String title, String text, String hint, OnscreenKeyboardType type) { real.getTextInput(l, title, text, hint, type); }
    @Override public void setOnscreenKeyboardVisible(boolean visible) { real.setOnscreenKeyboardVisible(visible); }
    @Override public void setOnscreenKeyboardVisible(boolean visible, OnscreenKeyboardType type) { real.setOnscreenKeyboardVisible(visible, type); }
    @Override public void openTextInputField(NativeInputConfiguration configuration) { real.openTextInputField(configuration); }
    @Override public void closeTextInputField(boolean sendReturn) { real.closeTextInputField(sendReturn); }
    @Override public void closeTextInputField(boolean sendReturn, NativeInputConfiguration.NativeInputCloseCallback callback) { real.closeTextInputField(sendReturn, callback); }
    @Override public boolean isTextInputFieldOpened() { return real.isTextInputFieldOpened(); }
    @Override public void setKeyboardHeightObserver(KeyboardHeightObserver observer) { real.setKeyboardHeightObserver(observer); }
    @Override public void vibrate(int milliseconds) { real.vibrate(milliseconds); }
    @Override public void vibrate(int milliseconds, boolean fallback) { real.vibrate(milliseconds, fallback); }
    @Override public void vibrate(int milliseconds, int amplitude, boolean fallback) { real.vibrate(milliseconds, amplitude, fallback); }
    @Override public void vibrate(VibrationType type) { real.vibrate(type); }
    @Override public float getAzimuth() { return real.getAzimuth(); }
    @Override public float getPitch() { return real.getPitch(); }
    @Override public float getRoll() { return real.getRoll(); }
    @Override public void getRotationMatrix(float[] matrix) { real.getRotationMatrix(matrix); }
    @Override public long getCurrentEventTime() { return real.getCurrentEventTime(); }
    @Override public void setCatchKey(int keycode, boolean catchKey) { real.setCatchKey(keycode, catchKey); }
    @Override public boolean isCatchKey(int keycode) { return real.isCatchKey(keycode); }
    @Override public boolean isPeripheralAvailable(Peripheral peripheral) { return real.isPeripheralAvailable(peripheral); }
    @Override public int getRotation() { return real.getRotation(); }
    @Override public Orientation getNativeOrientation() { return real.getNativeOrientation(); }
    @Override public void setCursorCatched(boolean catched) { real.setCursorCatched(catched); }
    @Override public boolean isCursorCatched() { return real.isCursorCatched(); }
    @Override public void setCursorPosition(int x, int y) { real.setCursorPosition(x, y); }
}
