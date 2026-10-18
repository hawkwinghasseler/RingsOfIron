package com.ringsofiron.uihud.modding;

import com.ringsofiron.moddingdata.ModManifest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ModTrustDialogTest {

    private static ModManifest mod(String id, String version) {
        ModManifest m = new ModManifest();
        m.id = id;
        m.version = version;
        return m;
    }

    @Test
    void rememberedAnswerIgnoresOrderButNotVersions() {
        String ab = ModTrustDialog.rememberKey(Arrays.asList(mod("a", "1.0"), mod("b", "2.0")));
        String ba = ModTrustDialog.rememberKey(Arrays.asList(mod("b", "2.0"), mod("a", "1.0")));
        String updated = ModTrustDialog.rememberKey(Arrays.asList(mod("a", "1.1"), mod("b", "2.0")));
        assertEquals(ab, ba);
        assertNotEquals(ab, updated);
    }
}
