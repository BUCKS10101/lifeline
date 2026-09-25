-- Built-in exercises and templates (owner_id IS NULL): visible to everyone, read-only through the API.
-- New built-ins are added by new migrations; existing names are not renamed, since history links to them.

INSERT INTO exercises (id, owner_id, name, primary_muscle_group, equipment)
SELECT gen_random_uuid(), NULL, v.name, v.muscle, v.equipment
FROM (VALUES
    -- Chest
    ('Barbell Bench Press',          'CHEST',      'BARBELL'),
    ('Incline Barbell Bench Press',  'CHEST',      'BARBELL'),
    ('Dumbbell Bench Press',         'CHEST',      'DUMBBELL'),
    ('Incline Dumbbell Press',       'CHEST',      'DUMBBELL'),
    ('Cable Fly',                    'CHEST',      'CABLE'),
    ('Push-Up',                      'CHEST',      'BODYWEIGHT'),
    ('Chest Dip',                    'CHEST',      'BODYWEIGHT'),
    -- Shoulders
    ('Overhead Press',               'SHOULDERS',  'BARBELL'),
    ('Dumbbell Shoulder Press',      'SHOULDERS',  'DUMBBELL'),
    ('Lateral Raise',                'SHOULDERS',  'DUMBBELL'),
    ('Rear Delt Fly',                'SHOULDERS',  'DUMBBELL'),
    ('Face Pull',                    'SHOULDERS',  'CABLE'),
    -- Triceps
    ('Triceps Pushdown',             'TRICEPS',    'CABLE'),
    ('Overhead Triceps Extension',   'TRICEPS',    'DUMBBELL'),
    ('Skull Crusher',                'TRICEPS',    'BARBELL'),
    ('Close-Grip Bench Press',       'TRICEPS',    'BARBELL'),
    -- Back
    ('Deadlift',                     'BACK',       'BARBELL'),
    ('Pull-Up',                      'BACK',       'BODYWEIGHT'),
    ('Chin-Up',                      'BACK',       'BODYWEIGHT'),
    ('Lat Pulldown',                 'BACK',       'CABLE'),
    ('Barbell Row',                  'BACK',       'BARBELL'),
    ('Seated Cable Row',             'BACK',       'CABLE'),
    ('One-Arm Dumbbell Row',         'BACK',       'DUMBBELL'),
    ('T-Bar Row',                    'BACK',       'BARBELL'),
    -- Biceps
    ('Barbell Curl',                 'BICEPS',     'BARBELL'),
    ('Dumbbell Curl',                'BICEPS',     'DUMBBELL'),
    ('Hammer Curl',                  'BICEPS',     'DUMBBELL'),
    ('Preacher Curl',                'BICEPS',     'MACHINE'),
    -- Quads
    ('Barbell Back Squat',           'QUADS',      'BARBELL'),
    ('Front Squat',                  'QUADS',      'BARBELL'),
    ('Leg Press',                    'QUADS',      'MACHINE'),
    ('Leg Extension',                'QUADS',      'MACHINE'),
    ('Bulgarian Split Squat',        'QUADS',      'DUMBBELL'),
    ('Hack Squat',                   'QUADS',      'MACHINE'),
    -- Hamstrings and glutes
    ('Romanian Deadlift',            'HAMSTRINGS', 'BARBELL'),
    ('Leg Curl',                     'HAMSTRINGS', 'MACHINE'),
    ('Hip Thrust',                   'GLUTES',     'BARBELL'),
    ('Glute Bridge',                 'GLUTES',     'BODYWEIGHT'),
    -- Calves
    ('Standing Calf Raise',          'CALVES',     'MACHINE'),
    ('Seated Calf Raise',            'CALVES',     'MACHINE'),
    -- Core and other
    ('Plank',                        'CORE',       'BODYWEIGHT'),
    ('Hanging Leg Raise',            'CORE',       'BODYWEIGHT'),
    ('Cable Crunch',                 'CORE',       'CABLE'),
    ('Ab Wheel Rollout',             'CORE',       'BODYWEIGHT'),
    ('Farmer''s Walk',               'FULL_BODY',  'DUMBBELL')
) AS v(name, muscle, equipment);

INSERT INTO workout_templates (id, owner_id, name, notes)
VALUES (gen_random_uuid(), NULL, 'Push', 'Chest, shoulders and triceps'),
       (gen_random_uuid(), NULL, 'Pull', 'Back and biceps'),
       (gen_random_uuid(), NULL, 'Legs', 'Quads, hamstrings, glutes and calves');

INSERT INTO workout_template_exercises (id, template_id, exercise_id, position, target_sets)
SELECT gen_random_uuid(), t.id, e.id, x.pos, x.sets
FROM (VALUES
    ('Push', 'Barbell Bench Press',        1, 4),
    ('Push', 'Incline Dumbbell Press',     2, 3),
    ('Push', 'Overhead Press',             3, 3),
    ('Push', 'Lateral Raise',              4, 3),
    ('Push', 'Triceps Pushdown',           5, 3),
    ('Push', 'Overhead Triceps Extension', 6, 3),
    ('Pull', 'Pull-Up',                    1, 4),
    ('Pull', 'Barbell Row',                2, 4),
    ('Pull', 'Lat Pulldown',               3, 3),
    ('Pull', 'Seated Cable Row',           4, 3),
    ('Pull', 'Face Pull',                  5, 3),
    ('Pull', 'Barbell Curl',               6, 3),
    ('Pull', 'Hammer Curl',                7, 3),
    ('Legs', 'Barbell Back Squat',         1, 4),
    ('Legs', 'Romanian Deadlift',          2, 3),
    ('Legs', 'Leg Press',                  3, 3),
    ('Legs', 'Leg Curl',                   4, 3),
    ('Legs', 'Leg Extension',              5, 3),
    ('Legs', 'Standing Calf Raise',        6, 4)
) AS x(template_name, exercise_name, pos, sets)
JOIN workout_templates t ON t.owner_id IS NULL AND t.name = x.template_name
JOIN exercises e ON e.owner_id IS NULL AND e.name = x.exercise_name;
