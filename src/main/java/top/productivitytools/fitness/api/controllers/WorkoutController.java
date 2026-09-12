package top.productivitytools.fitness.api.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import top.productivitytools.fitness.api.entities.Workout;
import top.productivitytools.fitness.api.entities.WorkoutExercise;
import top.productivitytools.fitness.api.entities.WorkoutSet;
import top.productivitytools.fitness.api.services.WorkoutService;
import top.productivitytools.fitness.api.dto.requests.AddExercisesRequest;
import top.productivitytools.fitness.api.dto.requests.AddSetRequest;
import top.productivitytools.fitness.api.dto.requests.DeleteSetRequest;
import top.productivitytools.fitness.api.dto.hevy.HevyImportRequest;
import top.productivitytools.fitness.api.dto.hevy.HevyImportResponse;
import top.productivitytools.fitness.api.dto.requests.DeleteWorkoutRequest;
import top.productivitytools.fitness.api.dto.requests.SaveSetRequest;
import top.productivitytools.fitness.api.services.hevy.HevyImportService;

import java.util.List;

@RestController
@RequestMapping({"/api/workout", "/workout"})
@RequiredArgsConstructor
public class WorkoutController {
    
    private final WorkoutService workoutService;
    private final HevyImportService hevyImportService;

    @PostMapping("/import/hevy")
    public HevyImportResponse importFromHevy(@RequestBody(required = false) HevyImportRequest request) {
        return hevyImportService.importWorkouts(request);
    }

    @GetMapping({"/list", ""})
    public List<Workout> getAllWorkouts() {
        return workoutService.getAllWorkouts();
    }

    @GetMapping ("/{workoutId}")
    public Workout getWorkoutDetails(
        @PathVariable (required=true) Long workoutId)
        {
        if (workoutId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workout ID must be provided in the URL path");
        }
        return workoutService.getWorkoutById(workoutId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"workout not found"));
        }

    @PostMapping({"/add", ""})
    public Workout addWorkout(@RequestBody Workout workout) {
        return workoutService.save(workout);
    }

    @RequestMapping(value = {"/{workoutId}/title", "/{workoutId}"}, method = {RequestMethod.PUT, RequestMethod.POST, RequestMethod.PATCH})
    public Workout updateWorkoutTitle(
            @PathVariable Long workoutId,
            @RequestBody Workout workout) {
        String newTitle = workout != null ? workout.getTitle() : null;
        if (newTitle == null || newTitle.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title cannot be empty");
        }
        return workoutService.updateTitle(workoutId, newTitle);
    }


    @PostMapping({"/exercise", "/exercises", "/{workoutId}/exercise", "/{workoutId}/exercises"})
    public Workout addExerciseToWorkout(
            @PathVariable(required = false) Long workoutId,
            @RequestBody AddExercisesRequest request) {
        Long targetWorkoutId = (workoutId != null) ? workoutId : (request != null ? request.workoutId() : null);
        if (targetWorkoutId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workout ID must be provided in the URL path or in the request body");
        }
        return workoutService.addExercisesToWorkout(targetWorkoutId, request);
    }

    @PostMapping("/addSet")
    public Workout addSet(@RequestBody AddSetRequest request) {
        return workoutService.addSet(request);
    }

    @RequestMapping(value = "/saveSet", method = {RequestMethod.POST, RequestMethod.PUT})
    public WorkoutSet saveSet(@RequestBody SaveSetRequest request) {
        return workoutService.saveSet(request);
    }

    @PostMapping("/deleteSet")
    public boolean deleteSet(@RequestBody DeleteSetRequest request) {
        if (request == null || request.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set ID must be provided in request body");
        }
        return workoutService.deleteSet(request.id());
    }

    public record SaveExerciseNotesRequest(
        Long workoutExerciseId,
        String notes
    ) {}

    @PostMapping("/updateExerciseNotes")
    public WorkoutExercise updateExerciseNotes(@RequestBody SaveExerciseNotesRequest request) {
        if (request == null || request.workoutExerciseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WorkoutExercise ID must be provided in request body");
        }
        return workoutService.updateExerciseNotes(request.workoutExerciseId(), request.notes());
    }

    public record CompleteWorkoutRequest(
        Long workoutId
    ) {}

    @PostMapping("/completeWorkout")
    public Workout completeWorkout(@RequestBody CompleteWorkoutRequest request) {
        if (request == null || request.workoutId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workout ID must be provided in request body");
        }
        return workoutService.completeWorkout(request.workoutId());
    }

    @PostMapping("/delete")
    public boolean deleteWorkout(@RequestBody DeleteWorkoutRequest request) {
        if (request == null || request.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workout ID must be provided in request body");
        }
        return workoutService.deleteWorkout(request.id());
    }
}
