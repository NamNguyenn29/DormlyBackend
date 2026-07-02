package com.example.DormlyBackend.util;

import com.example.DormlyBackend.entity.information.StudentProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonalityUtilTest {

    @Test
    void testMapSleepTime() {
        // Before 22:00
        assertEquals(10, PersonalityUtil.mapSleepTime("21:30"));
        assertEquals(10, PersonalityUtil.mapSleepTime("20:00"));

        // 22:00–23:00
        assertEquals(25, PersonalityUtil.mapSleepTime("22:00"));
        assertEquals(25, PersonalityUtil.mapSleepTime("22:30"));

        // 23:00–00:00
        assertEquals(50, PersonalityUtil.mapSleepTime("23:00"));
        assertEquals(50, PersonalityUtil.mapSleepTime("23:59"));

        // 00:00–01:00 (Midnight handling)
        assertEquals(75, PersonalityUtil.mapSleepTime("00:00"));
        assertEquals(75, PersonalityUtil.mapSleepTime("00:30"));

        // After 01:00
        assertEquals(90, PersonalityUtil.mapSleepTime("01:00"));
        assertEquals(90, PersonalityUtil.mapSleepTime("02:00"));
        assertEquals(90, PersonalityUtil.mapSleepTime("01:30"));
    }

    @Test
    void testMapWakeTime() {
        // Before 06:00
        assertEquals(10, PersonalityUtil.mapWakeTime("05:30"));
        assertEquals(10, PersonalityUtil.mapWakeTime("05:00"));

        // 06:00–07:00
        assertEquals(25, PersonalityUtil.mapWakeTime("06:00"));
        assertEquals(25, PersonalityUtil.mapWakeTime("06:45"));

        // 07:00–08:00
        assertEquals(50, PersonalityUtil.mapWakeTime("07:00"));
        assertEquals(50, PersonalityUtil.mapWakeTime("07:30"));

        // 08:00–09:00
        assertEquals(75, PersonalityUtil.mapWakeTime("08:00"));
        assertEquals(75, PersonalityUtil.mapWakeTime("08:15"));

        // After 09:00
        assertEquals(90, PersonalityUtil.mapWakeTime("09:00"));
        assertEquals(90, PersonalityUtil.mapWakeTime("10:00"));
    }

    @Test
    void testMapPreference() {
        assertEquals(10, PersonalityUtil.mapPreference(1));
        assertEquals(30, PersonalityUtil.mapPreference(2));
        assertEquals(50, PersonalityUtil.mapPreference(3));
        assertEquals(70, PersonalityUtil.mapPreference(4));
        assertEquals(90, PersonalityUtil.mapPreference(5));
        assertEquals(50, PersonalityUtil.mapPreference(null));
        assertEquals(50, PersonalityUtil.mapPreference(6));
    }

    @Test
    void testResolveTraits() {
        StudentProfile profile = new StudentProfile();
        profile.setSleepRhythmScore(10);
        profile.setWakeRhythmScore(25);
        profile.setQuietPreferenceScore(30);
        profile.setSocialPreferenceScore(70);
        profile.setStudyHabitScore(50);
        profile.setRoutineStrictnessScore(90);
        profile.setAdaptabilityScore(90);

        List<String> traits = PersonalityUtil.resolveTraits(profile);
        assertTrue(traits.contains("Very early sleeper"));
        assertTrue(traits.contains("Early riser"));
        assertTrue(traits.contains("Quiet-oriented"));
        assertTrue(traits.contains("Social"));
        assertTrue(traits.contains("Flexible study style"));
        assertTrue(traits.contains("Highly structured routine"));
        assertTrue(traits.contains("Very high adaptability"));
    }
}
