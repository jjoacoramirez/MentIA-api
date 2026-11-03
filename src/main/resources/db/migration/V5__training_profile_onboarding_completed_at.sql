-- BACK-02: primera vez que el usuario completó el onboarding (el evento onboarding_completed se registra una sola vez).

ALTER TABLE training_profiles ADD COLUMN onboarding_completed_at TIMESTAMPTZ;
