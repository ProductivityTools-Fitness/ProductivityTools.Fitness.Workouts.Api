package top.productivitytools.fitness.api.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import top.productivitytools.fitness.api.dto.requests.AddExercisesRequest;
import top.productivitytools.fitness.api.dto.requests.AddSetRequest;
import top.productivitytools.fitness.api.dto.requests.SaveSetRequest;
import top.productivitytools.fitness.api.dto.responses.WorkoutSummaryDto;
import top.productivitytools.fitness.api.entities.Exercise;
import top.productivitytools.fitness.api.entities.FitnessUser;
import top.productivitytools.fitness.api.entities.Workout;
import top.productivitytools.fitness.api.entities.WorkoutExercise;
import top.productivitytools.fitness.api.entities.WorkoutSet;
import top.productivitytools.fitness.api.repositories.ExerciseRepository;
import top.productivitytools.fitness.api.repositories.FitnessUserRepository;
import top.productivitytools.fitness.api.repositories.WorkoutExerciseRepository;
import top.productivitytools.fitness.api.repositories.WorkoutRepository;
import top.productivitytools.fitness.api.repositories.WorkoutSetRepository;
import top.productivitytools.fitness.api.security.UserContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkoutService {
    private final WorkoutRepository repository;
    private final FitnessUserRepository userRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final WorkoutSetRepository workoutSetRepository;

    public List<WorkoutSummaryDto> getAllWorkouts() {
        FitnessUser user = getCurrentUser();
        return repository.findSummariesByUserId(user.getId());
    }

    public List<WorkoutSummaryDto> getWorkoutsByUserId(Long userId) {
        FitnessUser currentUser = getCurrentUser();
        if (userId != null && !userId.equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access workouts of another user");
        }
        return repository.findSummariesByUserId(currentUser.getId());
    }

    public Optional<Workout> getWorkoutById(Long id) {
        FitnessUser currentUser = getCurrentUser();
        return repository.findById(id)
                .filter(w -> w.getUser() == null || (w.getUser().getId() != null && w.getUser().getId().equals(currentUser.getId())));
    }

    @Transactional
    public Workout save(Workout workout) {
        FitnessUser currentUser = getCurrentUser();
        workout.setUser(currentUser);

        if (workout.getStartTime() == null) {
            workout.setStartTime(OffsetDateTime.now());
        }
        boolean isNew = workout.getId() == null;
        if (isNew) {
            if (workout.getWorkoutNumber() == null) {
                int nextNumber = repository.findMaxWorkoutNumberByUserId(currentUser.getId()) + 1;
                workout.setWorkoutNumber(nextNumber);
            }
            boolean needsTitle = workout.getTitle() == null
                    || workout.getTitle().isBlank()
                    || workout.getTitle().equalsIgnoreCase("Log Workout")
                    || workout.getTitle().equalsIgnoreCase("New workout");

            if (needsTitle) {
                workout.setTitle("Trening #" + workout.getWorkoutNumber());
            }
        }
        return repository.save(workout);
    }

    @Transactional
    public Workout updateTitle(Long workoutId, String title) {
        Workout workout = repository.findById(workoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found with id: " + workoutId));
        workout.setTitle(title);
        return repository.save(workout);
    }


    @Transactional
    public Workout addExercisesToWorkout(Long workoutId, AddExercisesRequest request) {
        Workout workout = repository.findById(workoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found with id: " + workoutId));

        if (workout.getUser() == null || workout.getUser().getId() == null) {
            workout.setUser(getCurrentUser());
            workout = repository.save(workout);
        }

        if (request == null || request.exerciseIds() == null || request.exerciseIds().isEmpty()) {
            return workout;
        }

        Long userId = workout.getUser().getId();
        int nextOrderIndex = workout.getExercises().size() + 1;

        for (Long exerciseId : request.exerciseIds()) {
            Exercise exercise = exerciseRepository.findById(exerciseId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found with id: " + exerciseId));

            WorkoutExercise workoutExercise = new WorkoutExercise();
            workoutExercise.setWorkout(workout);
            workoutExercise.setExercise(exercise);
            workoutExercise.setOrderIndex(nextOrderIndex++);

            Optional<WorkoutExercise> pastExerciseOpt = findPastExercise(userId, exerciseId, workout.getId(), workout.getStartTime());

            int defaultRestSeconds = workout.getUser() != null ? workout.getUser().getDefaultRestTimerSeconds() : 90;
            Integer restTimer = pastExerciseOpt.map(WorkoutExercise::getRestTimerSeconds)
                    .filter(rt -> rt != null && rt > 0)
                    .orElse(defaultRestSeconds);
            workoutExercise.setRestTimerSeconds(restTimer);

            if (pastExerciseOpt.isPresent()) {
                WorkoutExercise pastExercise = pastExerciseOpt.get();
                List<WorkoutSet> pastSets = pastExercise.getSets().stream()
                        .sorted(Comparator.comparing(WorkoutSet::getSetNumber, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList();

                if (!pastSets.isEmpty()) {
                    for (WorkoutSet pastSet : pastSets) {
                        workoutExercise.addSet(
                                pastSet.getWeightKg(),
                                pastSet.getReps(),
                                pastSet.getWeightKg(),
                                pastSet.getReps()
                        );
                    }
                } else {
                    workoutExercise.addSet();
                }
            } else {
                // Add an initial empty set if the user has not done this exercise in the past
                workoutExercise.addSet();
            }

            workoutExerciseRepository.save(workoutExercise);
            workout.getExercises().add(workoutExercise);
        }

        return repository.save(workout);
    }

    @Transactional
    public Workout addSet(AddSetRequest request) {
        if (request == null || request.workoutId() == null || request.exerciseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "workoutId and exerciseId must be provided");
        }

        Workout workout = repository.findById(request.workoutId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found with id: " + request.workoutId()));

        WorkoutExercise workoutExercise = workout.getExercises().stream()
                .filter(we -> (we.getExercise() != null && request.exerciseId().equals(we.getExercise().getId()))
                        || request.exerciseId().equals(we.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise with id " + request.exerciseId() + " not found in workout " + request.workoutId()));

        int nextSetNumber = workoutExercise.getSets().size() + 1;
        Long userId = workout.getUser() != null ? workout.getUser().getId() : null;
        Long exerciseEntityId = workoutExercise.getExercise() != null ? workoutExercise.getExercise().getId() : null;

        Optional<WorkoutSet> pastSetOpt = Optional.empty();
        if (userId != null && exerciseEntityId != null) {
            pastSetOpt = findPastExercise(userId, exerciseEntityId, workout.getId(), workout.getStartTime())
                    .flatMap(pe -> pe.getSets().stream()
                            .filter(s -> s.getSetNumber() != null && s.getSetNumber().equals(nextSetNumber))
                            .findFirst());
        }

        WorkoutSet newSet;
        if (pastSetOpt.isPresent()) {
            WorkoutSet pastSet = pastSetOpt.get();
            newSet = workoutExercise.addSet(
                    pastSet.getWeightKg(),
                    pastSet.getReps(),
                    pastSet.getWeightKg(),
                    pastSet.getReps()
            );
        } else {
            newSet = workoutExercise.addSet();
        }

        workoutSetRepository.save(newSet);
        workoutExerciseRepository.save(workoutExercise);
        return repository.save(workout);
    }

    public Optional<WorkoutExercise> findPastExercise(Long userId, Long exerciseId, Long currentWorkoutId, OffsetDateTime currentWorkoutStartTime) {
        if (userId == null || exerciseId == null) {
            return Optional.empty();
        }
        List<WorkoutExercise> past = workoutExerciseRepository.findPastExercises(
                userId, exerciseId, currentWorkoutId, currentWorkoutStartTime, PageRequest.of(0, 1)
        );
        return past.isEmpty() ? Optional.empty() : Optional.of(past.get(0));
    }

    @Transactional
    public WorkoutSet saveSet(SaveSetRequest request) {
        if (request == null || request.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set ID must be provided in request body");
        }

        WorkoutSet workoutSet = workoutSetRepository.findById(request.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout set not found with id: " + request.id()));

        if (request.kg() != null) {
            workoutSet.setWeightKg(request.kg());
        }
        if (request.reps() != null) {
            workoutSet.setReps(request.reps());
        }
        if (request.status() != null) {
            workoutSet.setIsCompleted(request.status());
        }

        return workoutSetRepository.save(workoutSet);
    }

    @Transactional
    public boolean deleteSet(Long setId) {
        if (setId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set ID must be provided");
        }

        WorkoutSet workoutSet = workoutSetRepository.findById(setId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout set not found with id: " + setId));

        WorkoutExercise workoutExercise = workoutSet.getWorkoutExercise();
        if (workoutExercise != null) {
            workoutExercise.getSets().removeIf(s -> setId.equals(s.getId()));
            int number = 1;
            for (WorkoutSet s : workoutExercise.getSets()) {
                s.setSetNumber(number++);
            }
            workoutExerciseRepository.save(workoutExercise);
        } else {
            workoutSetRepository.delete(workoutSet);
        }

        return true;
    }

    public FitnessUser getCurrentUser() {
        FitnessUser user = UserContext.getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        return user;
    }

    @Transactional
    public WorkoutExercise updateExerciseNotes(Long workoutExerciseId, String notes) {
        if (workoutExerciseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WorkoutExercise ID must be provided");
        }
        WorkoutExercise workoutExercise = workoutExerciseRepository.findById(workoutExerciseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "WorkoutExercise not found with id: " + workoutExerciseId));
        workoutExercise.setNotes(notes);
        return workoutExerciseRepository.save(workoutExercise);
    }

    @Transactional
    public Workout completeWorkout(Long workoutId) {
        if (workoutId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workout ID must be provided");
        }
        Workout workout = repository.findById(workoutId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found with id: " + workoutId));

        OffsetDateTime now = OffsetDateTime.now();
        workout.setStatus("COMPLETED");
        workout.setEndTime(now);
        if (workout.getStartTime() != null) {
            long duration = java.time.Duration.between(workout.getStartTime(), now).getSeconds();
            workout.setDurationSeconds((int) Math.max(0, duration));
        }
        workout.setUpdatedAt(now);
        return repository.save(workout);
    }

    @Transactional
    public boolean deleteWorkout(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workout ID must be provided");
        }
        FitnessUser currentUser = getCurrentUser();
        Workout workout = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found with id: " + id));

        if (workout.getUser() != null && workout.getUser().getId() != null
                && !workout.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete workout of another user");
        }

        repository.delete(workout);
        return true;
    }
}
