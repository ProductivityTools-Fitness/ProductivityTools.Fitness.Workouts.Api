-- Synchronize existing exercises in the exercise table with canonical metadata
-- from Fitness.Catalog.Api (name, categories, muscles, instructions) while preserving
-- exercise.id so that all existing workout_exercise rows remain linked.

UPDATE exercise
SET catalog_exercise_id = 'fp_lever_high_row'
WHERE catalog_exercise_id IS NULL
  AND LOWER(name) = 'lever one arm lateral high row'
  AND NOT EXISTS (SELECT 1 FROM exercise WHERE catalog_exercise_id = 'fp_lever_high_row');

UPDATE exercise
SET catalog_exercise_id = 'fp_cable_lateral_raise'
WHERE catalog_exercise_id IS NULL
  AND LOWER(name) = 'cable one arm lateral raise'
  AND NOT EXISTS (SELECT 1 FROM exercise WHERE catalog_exercise_id = 'fp_cable_lateral_raise');

DO $$
DECLARE
    rec RECORD;
    target_id BIGINT;
    dup_id BIGINT;
BEGIN
    FOR rec IN
        SELECT * FROM (VALUES
            ('exdb_rowing_machine', 'rowing machine', 'cardio', 'leverage machine', 'cardiovascular system', '["lats", "upper back", "quads", "biceps"]', '["Sit on the rowing machine, secure your feet in the straps, and grasp the handle with an overhand grip.", "Drive forcefully with your legs first, then lean back slightly and pull the handle to your lower ribs.", "Return to the starting position by extending your arms, hinging at the hips, and bending your knees."]'),
            ('exdb_sled_push', 'sled push', 'upper legs', 'sled machine', 'quads', '["glutes", "calves", "core", "shoulders"]', '["Grip the sled uprights firmly, lean your torso forward at roughly a 45-degree angle, and brace your core.", "Drive forcefully through the balls of your feet, taking powerful, steady strides to push the sled forward.", "Maintain a neutral spine and flat back throughout the entire push."]'),
            ('exdb_wall_sit', 'wall sit', 'upper legs', 'body weight', 'quads', '["glutes", "calves", "hamstrings"]', '["Stand with your back flat against a wall and your feet shoulder-width apart about two feet away from the wall.", "Slide down until your thighs are parallel to the floor and your knees form a 90-degree angle.", "Keep your back flat against the wall and hold this isometric position for the target duration."]'),
            ('fp_arnold_press', 'arnold press', 'shoulders', 'body weight', 'delts', '[]', '["Perform arnold press with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_barbell_bent_over_row', 'barbell bent over row', 'back', 'barbell', 'lats', '[]', '["Perform barbell bent over row with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_barbell_curl', 'barbell curl', 'upper arms', 'barbell', 'biceps', '[]', '["Perform barbell curl with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_barbell_front_raise', 'barbell front raise', 'shoulders', 'barbell', 'delts', '[]', '["Perform barbell front raise with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_barbell_hip_thrusts', 'barbell hip thrusts', 'upper legs', 'barbell', 'glutes', '[]', '["Perform barbell hip thrusts with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_barbell_military_press', 'barbell military press', 'shoulders', 'barbell', 'delts', '[]', '["Perform barbell military press with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_bench_press', 'bench press', 'chest', 'body weight', 'pectorals', '[]', '["Perform bench press with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_bent_over_dumbbell_row', 'bent over dumbbell row', 'back', 'dumbbell', 'lats', '[]', '["Perform bent over dumbbell row with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_boxing_right_cross', 'boxing right cross', 'waist', 'body weight', 'abs', '[]', '["Perform boxing right cross with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_cable_lateral_raise', 'cable lateral raise', 'shoulders', 'cable', 'delts', '[]', '["Perform cable lateral raise with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_calf_raise', 'calf raise', 'lower legs', 'body weight', 'calves', '[]', '["Perform calf raise with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_close_grip_cable_row', 'close grip cable row', 'back', 'cable', 'lats', '[]', '["Perform close grip cable row with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_crunch', 'crunch', 'waist', 'body weight', 'abs', '[]', '["Perform crunch with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_deadlift', 'deadlift', 'back', 'body weight', 'lats', '[]', '["Perform deadlift with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_decline_barbell_bench_press', 'decline barbell bench press', 'chest', 'barbell', 'pectorals', '[]', '["Perform decline barbell bench press with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_dumbbell_bulgarian_split_squat', 'dumbbell bulgarian split squat', 'upper legs', 'dumbbell', 'quads', '[]', '["Perform dumbbell bulgarian split squat with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_dumbbell_deadlift', 'dumbbell deadlift', 'back', 'dumbbell', 'lats', '[]', '["Perform dumbbell deadlift with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_dumbbell_front_raise', 'dumbbell front raise', 'shoulders', 'dumbbell', 'delts', '[]', '["Perform dumbbell front raise with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_dumbbell_goblet_squat', 'dumbbell goblet squat', 'upper legs', 'dumbbell', 'quads', '[]', '["Perform dumbbell goblet squat with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_dumbbell_preacher_curl', 'dumbbell preacher curl', 'upper arms', 'dumbbell', 'biceps', '[]', '["Perform dumbbell preacher curl with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_dumbbell_pullover', 'dumbbell pullover', 'waist', 'dumbbell', 'abs', '[]', '["Perform dumbbell pullover with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_dumbbell_renegade_row', 'dumbbell renegade row', 'back', 'dumbbell', 'lats', '[]', '["Perform dumbbell renegade row with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_dumbbell_side_bend', 'dumbbell side bend', 'waist', 'dumbbell', 'abs', '[]', '["Perform dumbbell side bend with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_dumbbell_standing_palms_in_press', 'dumbbell standing palms in press', 'waist', 'dumbbell', 'abs', '[]', '["Perform dumbbell standing palms in press with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_dumbbell_triceps_extension', 'dumbbell triceps extension', 'upper arms', 'dumbbell', 'triceps', '[]', '["Perform dumbbell triceps extension with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_hammer_curl', 'hammer curl', 'upper arms', 'body weight', 'biceps', '[]', '["Perform hammer curl with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_hanging_leg_raises', 'hanging leg raises', 'waist', 'body weight', 'abs', '[]', '["Perform hanging leg raises with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_high_plank', 'high plank', 'waist', 'body weight', 'abs', '[]', '["Perform high plank with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_hip_adduction_machine', 'hip adduction machine', 'upper legs', 'leverage machine', 'glutes', '[]', '["Perform hip adduction machine with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_horizontal_leg_press', 'horizontal leg press', 'upper legs', 'leverage machine', 'quads', '[]', '["Perform horizontal leg press with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_incline_barbell_bench_press', 'incline barbell bench press', 'chest', 'barbell', 'pectorals', '[]', '["Perform incline barbell bench press with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_incline_dumbbell_press', 'incline dumbbell press', 'waist', 'dumbbell', 'abs', '[]', '["Perform incline dumbbell press with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_kneeling_cable_crunch', 'kneeling cable crunch', 'waist', 'cable', 'abs', '[]', '["Perform kneeling cable crunch with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_landmine_twist', 'landmine twist', 'waist', 'body weight', 'abs', '[]', '["Perform landmine twist with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_lat_pulldown', 'lat pulldown', 'back', 'cable', 'lats', '[]', '["Perform lat pulldown with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_leg_extension', 'leg extension', 'upper legs', 'body weight', 'quads', '[]', '["Perform leg extension with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_leg_raise', 'leg raise', 'waist', 'body weight', 'abs', '[]', '["Perform leg raise with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_lever_chest_press', 'lever chest press', 'chest', 'leverage machine', 'pectorals', '[]', '["Perform lever chest press with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_lever_front_pulldown', 'lever front pulldown', 'back', 'cable', 'lats', '[]', '["Perform lever front pulldown with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_lever_high_row', 'lever high row', 'back', 'leverage machine', 'lats', '[]', '["Perform lever high row with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_lever_seated_calf_raise', 'lever seated calf raise', 'lower legs', 'leverage machine', 'calves', '[]', '["Perform lever seated calf raise with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_lever_shoulder_press', 'lever shoulder press', 'shoulders', 'leverage machine', 'delts', '[]', '["Perform lever shoulder press with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_pull_up', 'pull up', 'back', 'body weight', 'lats', '[]', '["Perform pull up with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_push_down', 'push down', 'waist', 'body weight', 'abs', '[]', '["Perform push down with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_running', 'running', 'cardio', 'body weight', 'cardiovascular system', '[]', '["Perform running with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_seated_crunch_machine', 'seated crunch machine', 'waist', 'leverage machine', 'abs', '[]', '["Perform seated crunch machine with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_shadow_boxing', 'shadow boxing', 'waist', 'body weight', 'abs', '[]', '["Perform shadow boxing with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_squat', 'squat', 'upper legs', 'body weight', 'quads', '[]', '["Perform squat with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_standing_cable_low_to_high_twist', 'standing cable low to high twist', 'waist', 'cable', 'abs', '[]', '["Perform standing cable low to high twist with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_the_box_jump', 'the box jump', 'upper legs', 'body weight', 'quads', '[]', '["Perform the box jump with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_triceps_dip_machine', 'triceps dip machine', 'upper arms', 'leverage machine', 'triceps', '[]', '["Perform triceps dip machine with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_triceps_dips', 'triceps dips', 'upper arms', 'body weight', 'triceps', '[]', '["Perform triceps dips with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_weight_plate_front_raise', 'weight plate front raise', 'shoulders', 'sled machine', 'delts', '[]', '["Perform weight plate front raise with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_z_bar_curl', 'z bar curl', 'upper arms', 'body weight', 'biceps', '[]', '["Perform z bar curl with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]'),
            ('fp_z_bar_preacher_curl', 'z bar preacher curl', 'upper arms', 'body weight', 'biceps', '[]', '["Perform z bar preacher curl with controlled form and proper breathing.", "Focus on engaging the target muscles throughout the full range of motion."]')
        ) AS t(catalog_id, new_name, body_cat, equip_cat, target_musc, sec_musc, instr)
    LOOP
        SELECT id INTO target_id FROM exercise WHERE catalog_exercise_id = rec.catalog_id;
        IF target_id IS NOT NULL THEN
            FOR dup_id IN
                SELECT id FROM exercise
                WHERE user_id IS NULL
                  AND LOWER(name) = LOWER(rec.new_name)
                  AND id <> target_id
            LOOP
                UPDATE workout_exercise SET exercise_id = target_id WHERE exercise_id = dup_id;
                UPDATE workout_template_exercise SET exercise_id = target_id WHERE exercise_id = dup_id;
                DELETE FROM exercise WHERE id = dup_id;
            END LOOP;

            UPDATE exercise
            SET name               = rec.new_name,
                body_category      = rec.body_cat,
                equipment_category = rec.equip_cat,
                target_muscle      = rec.target_musc,
                secondary_muscles  = rec.sec_musc::jsonb,
                instructions       = rec.instr::jsonb,
                gif_url            = NULL
            WHERE id = target_id;
        END IF;
    END LOOP;
END $$;
