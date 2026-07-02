-- Add lifestyle preference and score fields to student_profiles
ALTER TABLE student_profiles ADD start_year INT NULL;
ALTER TABLE student_profiles ADD end_year INT NULL;
ALTER TABLE student_profiles ADD sleep_time NVARCHAR(10) NULL;
ALTER TABLE student_profiles ADD wake_up_time NVARCHAR(10) NULL;
ALTER TABLE student_profiles ADD sleep_score INT NULL;
ALTER TABLE student_profiles ADD wake_score INT NULL;
ALTER TABLE student_profiles ADD quiet_preference INT NULL;
ALTER TABLE student_profiles ADD quiet_preference_score INT NULL;
ALTER TABLE student_profiles ADD social_preference INT NULL;
ALTER TABLE student_profiles ADD social_preference_score INT NULL;
ALTER TABLE student_profiles ADD study_habit INT NULL;
ALTER TABLE student_profiles ADD study_habit_score INT NULL;
ALTER TABLE student_profiles ADD routine_strictness INT NULL;
ALTER TABLE student_profiles ADD routine_strictness_score INT NULL;
ALTER TABLE student_profiles ADD adaptability INT NULL;
ALTER TABLE student_profiles ADD adaptability_score INT NULL;
ALTER TABLE student_profiles ADD roommate_preference NVARCHAR(50) NULL;
ALTER TABLE student_profiles ADD friend_name NVARCHAR(100) NULL;
ALTER TABLE student_profiles ADD friend_student_id NVARCHAR(50) NULL;
ALTER TABLE student_profiles ADD friend_block NVARCHAR(50) NULL;
ALTER TABLE student_profiles ADD friend_floor NVARCHAR(50) NULL;
ALTER TABLE student_profiles ADD friend_room NVARCHAR(50) NULL;
ALTER TABLE student_profiles ADD sleep_rhythm_score INT NULL;
ALTER TABLE student_profiles ADD wake_rhythm_score INT NULL;
ALTER TABLE student_profiles ADD calculation_version NVARCHAR(50) NULL;
ALTER TABLE student_profiles ADD calculated_at DATETIME2 NULL;

-- Create student_profile_history table for personality profile snapshot history
CREATE TABLE student_profile_history (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    student_profile_id UNIQUEIDENTIFIER NOT NULL,
    start_year INT NULL,
    end_year INT NULL,
    sleep_time NVARCHAR(10) NULL,
    wake_up_time NVARCHAR(10) NULL,
    quiet_preference INT NULL,
    social_preference INT NULL,
    study_habit INT NULL,
    routine_strictness INT NULL,
    adaptability INT NULL,
    roommate_preference NVARCHAR(50) NULL,
    friend_name NVARCHAR(100) NULL,
    friend_student_id NVARCHAR(50) NULL,
    friend_block NVARCHAR(50) NULL,
    friend_floor NVARCHAR(50) NULL,
    friend_room NVARCHAR(50) NULL,
    
    sleep_rhythm_score INT NULL,
    wake_rhythm_score INT NULL,
    quiet_preference_score INT NULL,
    social_preference_score INT NULL,
    study_habit_score INT NULL,
    routine_strictness_score INT NULL,
    adaptability_score INT NULL,
    
    calculation_version NVARCHAR(50) NULL,
    calculated_at DATETIME2 NULL,
    trigger_reason NVARCHAR(50) NULL,
    changed_at DATETIME2 NOT NULL,
    CONSTRAINT fk_student_profile_history_profile FOREIGN KEY (student_profile_id) REFERENCES student_profiles(id) ON DELETE CASCADE
);
