package com.example.DormlyBackend.util;

import com.example.DormlyBackend.entity.information.StudentProfile;

import java.util.ArrayList;
import java.util.List;

public class PersonalityUtil {

    public static int mapSleepTime(String sleepTime) {
        if (sleepTime == null || sleepTime.isBlank()) {
            return 50;
        }
        try {
            String[] parts = sleepTime.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            double t = hour + (minute / 60.0);
            if (t >= 0 && t <= 12) {
                t += 24;
            }
            if (t < 22) {
                return 10;
            } else if (t < 23) {
                return 25;
            } else if (t < 24) {
                return 50;
            } else if (t < 25) {
                return 75;
            } else {
                return 90;
            }
        } catch (Exception e) {
            return 50;
        }
    }

    public static int mapWakeTime(String wakeTime) {
        if (wakeTime == null || wakeTime.isBlank()) {
            return 50;
        }
        try {
            String[] parts = wakeTime.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            double t = hour + (minute / 60.0);
            if (t < 6.0) {
                return 10;
            } else if (t < 7.0) {
                return 25;
            } else if (t < 8.0) {
                return 50;
            } else if (t < 9.0) {
                return 75;
            } else {
                return 90;
            }
        } catch (Exception e) {
            return 50;
        }
    }

    public static int mapPreference(Integer level) {
        if (level == null) return 50;
        return switch (level) {
            case 1 -> 10;
            case 2 -> 30;
            case 3 -> 50;
            case 4 -> 70;
            case 5 -> 90;
            default -> 50;
        };
    }

    public static List<String> resolveTraits(StudentProfile profile) {
        List<String> traits = new ArrayList<>();
        if (profile == null) return traits;

        // Sleep
        if (profile.getSleepRhythmScore() != null) {
            int score = profile.getSleepRhythmScore();
            if (score == 10) traits.add("Very early sleeper");
            else if (score == 25) traits.add("Early sleeper");
            else if (score == 50) traits.add("Normal sleeper");
            else if (score == 75) traits.add("Late sleeper");
            else if (score == 90) traits.add("Very late sleeper");
        }

        // Wake
        if (profile.getWakeRhythmScore() != null) {
            int score = profile.getWakeRhythmScore();
            if (score == 10) traits.add("Very early riser");
            else if (score == 25) traits.add("Early riser");
            else if (score == 50) traits.add("Normal riser");
            else if (score == 75) traits.add("Late riser");
            else if (score == 90) traits.add("Very late riser");
        }

        // Quiet
        if (profile.getQuietPreferenceScore() != null) {
            int score = profile.getQuietPreferenceScore();
            if (score == 10) traits.add("Strongly quiet-oriented");
            else if (score == 30) traits.add("Quiet-oriented");
            else if (score == 50) traits.add("Moderate noise preference");
            else if (score == 70) traits.add("Noise-tolerant");
            else if (score == 90) traits.add("Highly noise-tolerant");
        }

        // Social
        if (profile.getSocialPreferenceScore() != null) {
            int score = profile.getSocialPreferenceScore();
            if (score == 10) traits.add("Highly private");
            else if (score == 30) traits.add("Low interaction preference");
            else if (score == 50) traits.add("Balanced social preference");
            else if (score == 70) traits.add("Social");
            else if (score == 90) traits.add("Highly social");
        }

        // Study
        if (profile.getStudyHabitScore() != null) {
            int score = profile.getStudyHabitScore();
            if (score == 10) traits.add("Silent solo study");
            else if (score == 30) traits.add("Quiet individual study");
            else if (score == 50) traits.add("Flexible study style");
            else if (score == 70) traits.add("Collaborative study");
            else if (score == 90) traits.add("Active group study");
        }

        // Routine
        if (profile.getRoutineStrictnessScore() != null) {
            int score = profile.getRoutineStrictnessScore();
            if (score == 10) traits.add("Highly flexible routine");
            else if (score == 30) traits.add("Flexible routine");
            else if (score == 50) traits.add("Balanced routine");
            else if (score == 70) traits.add("Structured routine");
            else if (score == 90) traits.add("Highly structured routine");
        }

        // Adaptability
        if (profile.getAdaptabilityScore() != null) {
            int score = profile.getAdaptabilityScore();
            if (score == 10) traits.add("Low adaptability");
            else if (score == 30) traits.add("Gradual adaptability");
            else if (score == 50) traits.add("Moderate adaptability");
            else if (score == 70) traits.add("High adaptability");
            else if (score == 90) traits.add("Very high adaptability");
        }

        return traits;
    }
}
