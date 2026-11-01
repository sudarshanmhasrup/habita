# Program

The workout plan the Habita server sends to the app. The server reads this file on startup and
refuses to start if it is malformed, with the line number of the problem.

## Editing rules

- Exercise ids are permanent. History is stored by id, so rename an exercise freely, but never
  change or reuse its id. To replace an exercise, add a new one with a new id.
- Step is the default progression step. Use kg for weighted exercises (`2.5` is allowed) and reps or
  seconds for bodyweight ones. It can be changed per exercise from the app.
- Equipment is one of `dumbbell`, `machine`, `cable` or `bodyweight`. Measure is `reps` or
  `seconds`. Per side is `yes` or `no`.
- Target is reps, or seconds when the measure is `seconds`. Rest is in seconds.
- Rotation sets the suggestion order. A `fallback` workout is never suggested and does not move the
  rotation.

## Exercises

| Id                               | Name                             | Equipment  | Measure | Per side | Step |
|----------------------------------|----------------------------------|------------|---------|----------|------|
| dumbbell-bench-press             | Dumbbell Bench Press             | dumbbell   | reps    | no       | 2    |
| seated-cable-row                 | Seated Cable Row                 | cable      | reps    | no       | 5    |
| lat-pulldown                     | Lat Pulldown                     | cable      | reps    | no       | 5    |
| dumbbell-lateral-raise           | Dumbbell Lateral Raise           | dumbbell   | reps    | no       | 2    |
| reverse-pec-deck                 | Reverse Pec Deck                 | machine    | reps    | no       | 5    |
| biceps-curl                      | Biceps Curl                      | dumbbell   | reps    | no       | 2    |
| cable-triceps-pushdown           | Cable Triceps Pushdown           | cable      | reps    | no       | 2.5  |
| leg-press                        | Leg Press                        | machine    | reps    | no       | 5    |
| hip-thrust                       | Hip Thrust                       | machine    | reps    | no       | 5    |
| hamstring-curl                   | Hamstring Curl                   | machine    | reps    | no       | 5    |
| leg-extension                    | Leg Extension                    | machine    | reps    | no       | 5    |
| cable-crunch                     | Cable Crunch                     | cable      | reps    | no       | 5    |
| dumbbell-standing-calf-raise     | Dumbbell Standing Calf Raise     | dumbbell   | reps    | no       | 2    |
| incline-dumbbell-press           | Incline Dumbbell Press           | dumbbell   | reps    | no       | 2    |
| seated-dumbbell-shoulder-press   | Seated Dumbbell Shoulder Press   | dumbbell   | reps    | no       | 2    |
| one-arm-dumbbell-row             | One-Arm Dumbbell Row             | dumbbell   | reps    | yes      | 2    |
| pec-deck-fly                     | Pec Deck Fly                     | machine    | reps    | no       | 5    |
| cable-lateral-raise              | Cable Lateral Raise              | cable      | reps    | no       | 2.5  |
| incline-dumbbell-curl            | Incline Dumbbell Curl            | dumbbell   | reps    | no       | 2    |
| overhead-cable-triceps-extension | Overhead Cable Triceps Extension | cable      | reps    | no       | 2.5  |
| goblet-squat                     | Goblet Squat                     | dumbbell   | reps    | no       | 2    |
| dumbbell-step-up                 | Dumbbell Step-Up (bench)         | dumbbell   | reps    | yes      | 2    |
| cable-pull-through               | Cable Pull-Through               | cable      | reps    | no       | 5    |
| dumbbell-seated-calf-raise       | Dumbbell Seated Calf Raise       | dumbbell   | reps    | no       | 2    |
| plank                            | Plank                            | bodyweight | seconds | no       | 5    |

## Warm-ups

### Upper

- Id: upper
- Rest: 120

| Name                      | Amount                     |
|---------------------------|----------------------------|
| Treadmill walk            | 5 min, 4 mph, 5% incline   |
| Horizontal arm swings     | 20                         |
| Arm circles               | 20 forward + 20 backward   |
| Arm crossovers            | 20                         |
| Band pull-aparts          | 20                         |
| Banded dislocations       | 10                         |
| Banded external rotations | 10 each arm                |

### Lower

- Id: lower
- Rest: 120

| Name                 | Amount                       |
|----------------------|------------------------------|
| Treadmill walk       | 5 min, 4 mph, 5% incline     |
| Bodyweight squats    | 15                           |
| Leg swings           | 10 front-to-back each leg    |
| Hip circles          | 10 forward + 10 backward     |
| Glute bridges        | 15                           |
| Standing calf raises | 15                           |

### Full body

- Id: full-body
- Rest: 120

| Name              | Amount                   |
|-------------------|--------------------------|
| Treadmill walk    | 5 min, 4 mph, 5% incline |
| Bodyweight squats | 15                       |
| Arm circles       | 20 forward + 20 backward |
| Band pull-aparts  | 20                       |
| Glute bridges     | 15                       |

## Workouts

### Upper Body A

- Id: upper-a
- Warm-up: upper
- Rotation: 1

| Exercise               | Sets | Target | Rest |
|------------------------|------|--------|------|
| dumbbell-bench-press   | 3    | 12     | 90   |
| seated-cable-row       | 3    | 12     | 90   |
| lat-pulldown           | 3    | 12     | 90   |
| dumbbell-lateral-raise | 3    | 12     | 60   |
| reverse-pec-deck       | 3    | 12     | 60   |
| biceps-curl            | 3    | 12     | 60   |
| cable-triceps-pushdown | 3    | 12     | 60   |

### Lower Body A

- Id: lower-a
- Warm-up: lower
- Rotation: 2

| Exercise                     | Sets | Target | Rest |
|------------------------------|------|--------|------|
| leg-press                    | 3    | 12     | 90   |
| hip-thrust                   | 3    | 12     | 90   |
| hamstring-curl               | 3    | 12     | 90   |
| leg-extension                | 3    | 12     | 60   |
| cable-crunch                 | 3    | 12     | 60   |
| dumbbell-standing-calf-raise | 3    | 12     | 60   |

### Upper Body B

- Id: upper-b
- Warm-up: upper
- Rotation: 3

| Exercise                         | Sets | Target | Rest |
|----------------------------------|------|--------|------|
| incline-dumbbell-press           | 3    | 12     | 90   |
| seated-dumbbell-shoulder-press   | 3    | 12     | 90   |
| one-arm-dumbbell-row             | 3    | 12     | 90   |
| pec-deck-fly                     | 3    | 12     | 60   |
| cable-lateral-raise              | 3    | 12     | 60   |
| incline-dumbbell-curl            | 3    | 12     | 60   |
| overhead-cable-triceps-extension | 3    | 12     | 60   |

### Lower Body B

- Id: lower-b
- Warm-up: lower
- Rotation: 4

| Exercise                   | Sets | Target | Rest |
|----------------------------|------|--------|------|
| goblet-squat               | 3    | 12     | 90   |
| dumbbell-step-up           | 3    | 12     | 90   |
| cable-pull-through         | 3    | 12     | 90   |
| hamstring-curl             | 3    | 12     | 60   |
| dumbbell-seated-calf-raise | 3    | 12     | 60   |
| plank                      | 3    | 30     | 60   |

### Full Body

- Id: full-body
- Warm-up: full-body
- Rotation: fallback

| Exercise               | Sets | Target | Rest |
|------------------------|------|--------|------|
| dumbbell-bench-press   | 3    | 12     | 90   |
| leg-press              | 3    | 12     | 90   |
| seated-cable-row       | 3    | 12     | 90   |
| lat-pulldown           | 3    | 12     | 90   |
| hamstring-curl         | 3    | 12     | 60   |
| dumbbell-lateral-raise | 3    | 12     | 60   |
| biceps-curl            | 3    | 12     | 60   |
| cable-triceps-pushdown | 3    | 12     | 60   |