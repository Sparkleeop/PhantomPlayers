package me.sparklee.phantomplayers.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinQuitCompatibilityTest {

    @Test
    void neverEmitsAnnouncements() {
        JoinQuitCompatibility compatibility = new JoinQuitCompatibility();
        compatibility.recordSpawn("Alpha");
        compatibility.recordSpawn("Bravo");
        compatibility.recordDespawn("Alpha");
        assertEquals(2, compatibility.spawnObservations());
        assertEquals(1, compatibility.despawnObservations());
        assertEquals(0, compatibility.announcementsSent());
    }

    @Test
    void ignoresEmptyNamesAndDocumentsTheLimitation() {
        JoinQuitCompatibility compatibility = new JoinQuitCompatibility();
        compatibility.recordSpawn("");
        compatibility.recordSpawn(null);
        compatibility.recordDespawn(null);
        assertEquals(0, compatibility.spawnObservations());
        assertEquals(0, compatibility.despawnObservations());
        assertEquals("none", JoinQuitCompatibility.MECHANISM);
        assertTrue(compatibility.describe().contains("no announcements"));
        assertTrue(compatibility.describe().contains("native join/quit"));
    }
}
