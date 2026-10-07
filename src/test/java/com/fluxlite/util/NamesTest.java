package com.fluxlite.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void oneDeviceIsItsName() {
        assertEquals("装配机", Names.summarize(Collections.singletonList("装配机")));
    }

    @Test
    void sameDevicesAreCounted() {
        assertEquals("蒸汽粉碎机 ×3", Names.summarize(Arrays.asList("蒸汽粉碎机", "蒸汽粉碎机", "蒸汽粉碎机")));
    }

    @Test
    void mixedDevicesNameTheFirst() {
        assertEquals("装配机 +2", Names.summarize(Arrays.asList("装配机", "车床", "装配机")));
    }

    @Test
    void nothingIsEmpty() {
        assertEquals("", Names.summarize(Collections.emptyList()));
    }
}
