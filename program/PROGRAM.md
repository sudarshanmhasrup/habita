# Program

The workout plan the Habita server sends to the app. The server reads this file on startup and
refuses to start if it is malformed, with the line number of the problem.

## Editing rules

- Exercise ids are permanent. History is stored by id, so rename an exercise freely, but never
  change or reuse its id. To replace an exercise, add a new one with a new id.
- Never delete an exercise row: to stop doing it, take it out of the workouts and leave its row.
  As a safety net the server keeps serving exercises an older plan had, so history never
  disappears, but the row is the clean way.
- Step is the default progression step. Use kg for weighted exercises (`2.5` is allowed) and reps or
  seconds for bodyweight ones. It can be changed per exercise from the app.
- Equipment is one of `dumbbell`, `machine`, `cable` or `bodyweight`. Measure is `reps` or
  `seconds`. Per side is `yes` or `no`.
- Target is reps, or seconds when the measure is `seconds`. Rest is in seconds.
- Rotation sets the suggestion order. A `fallback` workout is never suggested and does not move the
  rotation.
- Alternatives list exercise ids, separated by commas. An exercise can be listed as an alternative
  without being in any workout.

## Exercises

| Id                                  | Name                                | Equipment  | Measure | Per side | Step |
|-------------------------------------|-------------------------------------|------------|---------|----------|------|
| dumbbell-bench-press                | Dumbbell Bench Press                | dumbbell   | reps    | no       | 2    |
| seated-cable-row                    | Seated Cable Row                    | cable      | reps    | no       | 5    |
| lat-pulldown                        | Lat Pulldown                        | cable      | reps    | no       | 5    |
| dumbbell-lateral-raise              | Dumbbell Lateral Raise              | dumbbell   | reps    | no       | 2    |
| reverse-pec-deck                    | Reverse Pec Deck                    | machine    | reps    | no       | 5    |
| biceps-curl                         | Biceps Curl                         | dumbbell   | reps    | no       | 2    |
| cable-triceps-pushdown              | Cable Triceps Pushdown              | cable      | reps    | no       | 2.5  |
| leg-press                           | Leg Press                           | machine    | reps    | no       | 5    |
| hip-thrust                          | Hip Thrust                          | machine    | reps    | no       | 5    |
| hamstring-curl                      | Hamstring Curl                      | machine    | reps    | no       | 5    |
| leg-extension                       | Leg Extension                       | machine    | reps    | no       | 5    |
| cable-crunch                        | Cable Crunch                        | cable      | reps    | no       | 5    |
| dumbbell-standing-calf-raise        | Dumbbell Standing Calf Raise        | dumbbell   | reps    | no       | 2    |
| incline-dumbbell-press              | Incline Dumbbell Press              | dumbbell   | reps    | no       | 2    |
| seated-dumbbell-shoulder-press      | Seated Dumbbell Shoulder Press      | dumbbell   | reps    | no       | 2    |
| one-arm-dumbbell-row                | One-Arm Dumbbell Row                | dumbbell   | reps    | yes      | 2    |
| pec-deck-fly                        | Pec Deck Fly                        | machine    | reps    | no       | 5    |
| cable-lateral-raise                 | Cable Lateral Raise                 | cable      | reps    | no       | 2.5  |
| incline-dumbbell-curl               | Incline Dumbbell Curl               | dumbbell   | reps    | no       | 2    |
| overhead-cable-triceps-extension    | Overhead Cable Triceps Extension    | cable      | reps    | no       | 2.5  |
| goblet-squat                        | Goblet Squat                        | dumbbell   | reps    | no       | 2    |
| dumbbell-step-up                    | Dumbbell Step-Up (bench)            | dumbbell   | reps    | yes      | 2    |
| cable-pull-through                  | Cable Pull-Through                  | cable      | reps    | no       | 5    |
| dumbbell-seated-calf-raise          | Dumbbell Seated Calf Raise          | dumbbell   | reps    | no       | 2    |
| plank                               | Plank                               | bodyweight | seconds | no       | 5    |
| machine-chest-press                 | Machine Chest Press                 | machine    | reps    | no       | 5    |
| incline-machine-press               | Incline Machine Press               | machine    | reps    | no       | 5    |
| dumbbell-fly                        | Dumbbell Fly                        | dumbbell   | reps    | no       | 2    |
| cable-fly                           | Cable Fly                           | cable      | reps    | no       | 2.5  |
| machine-row                         | Machine Row                         | machine    | reps    | no       | 5    |
| chest-supported-dumbbell-row        | Chest-Supported Dumbbell Row        | dumbbell   | reps    | no       | 2    |
| dumbbell-pullover                   | Dumbbell Pullover                   | dumbbell   | reps    | no       | 2    |
| machine-shoulder-press              | Machine Shoulder Press              | machine    | reps    | no       | 5    |
| dumbbell-rear-delt-fly              | Dumbbell Rear Delt Fly              | dumbbell   | reps    | no       | 2    |
| face-pull                           | Face Pull                           | cable      | reps    | no       | 2.5  |
| cable-curl                          | Cable Curl                          | cable      | reps    | no       | 2.5  |
| overhead-dumbbell-triceps-extension | Overhead Dumbbell Triceps Extension | dumbbell   | reps    | no       | 2    |
| hack-squat                          | Hack Squat                          | machine    | reps    | no       | 5    |
| dumbbell-split-squat                | Dumbbell Split Squat                | dumbbell   | reps    | yes      | 2    |
| dumbbell-romanian-deadlift          | Dumbbell Romanian Deadlift          | dumbbell   | reps    | no       | 2    |
| dumbbell-hip-thrust                 | Dumbbell Hip Thrust                 | dumbbell   | reps    | no       | 2    |
| machine-calf-raise                  | Machine Calf Raise                  | machine    | reps    | no       | 5    |
| hanging-knee-raise                  | Hanging Knee Raise                  | bodyweight | reps    | no       | 1    |

## Alternatives

What to do instead when a gym doesn't have the equipment, best first. In the app, "Not at this gym"
swaps an exercise for one of these until it's undone or a new gym clears every swap. Each
alternative is an exercise of its own, with its own history and weights.

| Exercise                         | Alternatives                                                          |
|----------------------------------|-----------------------------------------------------------------------|
| dumbbell-bench-press             | machine-chest-press, dumbbell-fly                                     |
| seated-cable-row                 | machine-row, chest-supported-dumbbell-row                             |
| lat-pulldown                     | dumbbell-pullover, one-arm-dumbbell-row                               |
| dumbbell-lateral-raise           | cable-lateral-raise                                                   |
| reverse-pec-deck                 | dumbbell-rear-delt-fly, face-pull                                     |
| biceps-curl                      | cable-curl, incline-dumbbell-curl                                     |
| cable-triceps-pushdown           | overhead-dumbbell-triceps-extension, overhead-cable-triceps-extension |
| leg-press                        | hack-squat, goblet-squat                                              |
| hip-thrust                       | dumbbell-hip-thrust, cable-pull-through                               |
| hamstring-curl                   | dumbbell-romanian-deadlift                                            |
| leg-extension                    | dumbbell-split-squat                                                  |
| cable-crunch                     | hanging-knee-raise                                                    |
| dumbbell-standing-calf-raise     | machine-calf-raise, dumbbell-seated-calf-raise                        |
| incline-dumbbell-press           | incline-machine-press, machine-chest-press                            |
| seated-dumbbell-shoulder-press   | machine-shoulder-press                                                |
| one-arm-dumbbell-row             | chest-supported-dumbbell-row, machine-row                             |
| pec-deck-fly                     | cable-fly, dumbbell-fly                                               |
| cable-lateral-raise              | dumbbell-lateral-raise                                                |
| incline-dumbbell-curl            | biceps-curl, cable-curl                                               |
| overhead-cable-triceps-extension | overhead-dumbbell-triceps-extension, cable-triceps-pushdown           |
| goblet-squat                     | leg-press, hack-squat                                                 |
| dumbbell-step-up                 | dumbbell-split-squat                                                  |
| cable-pull-through               | dumbbell-romanian-deadlift, dumbbell-hip-thrust                       |
| dumbbell-seated-calf-raise       | machine-calf-raise, dumbbell-standing-calf-raise                      |

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