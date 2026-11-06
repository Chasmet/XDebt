package com.chasmet.xdebt;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SkillMajAutoTest {
    @Test public void newerHandlesSemanticVersions() {
        assertTrue(SkillMajAuto.newer("1.0.12", "1.0.11"));
        assertTrue(SkillMajAuto.newer("1.1.0", "1.0.999"));
        assertTrue(SkillMajAuto.newer("2.0.0", "1.99.99"));
        assertFalse(SkillMajAuto.newer("1.0.11", "1.0.11"));
        assertFalse(SkillMajAuto.newer("1.0.9", "1.0.10"));
        assertFalse(SkillMajAuto.newer("v1.0.12", "1.0.11"));
        assertFalse(SkillMajAuto.newer("1.0.12-test", "1.0.11"));
        assertFalse(SkillMajAuto.newer("", "1.0.0"));
    }
}
