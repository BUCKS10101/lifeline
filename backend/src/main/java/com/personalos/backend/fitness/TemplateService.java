package com.personalos.backend.fitness;

import com.personalos.backend.common.error.ApiException;
import com.personalos.backend.common.paging.PagedResponse;
import com.personalos.backend.fitness.domain.Exercise;
import com.personalos.backend.fitness.domain.WorkoutTemplate;
import com.personalos.backend.fitness.domain.WorkoutTemplateExercise;
import com.personalos.backend.fitness.dto.TemplateDtos.*;
import com.personalos.backend.fitness.mapper.TemplateMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TemplateService {

    private static final int MAX_NAME = 100;
    private static final String COPY_SUFFIX = " (copy)";

    private final WorkoutTemplateRepository templates;
    private final WorkoutTemplateExerciseRepository templateExercises;
    private final ExerciseRepository exercises;
    private final TemplateMapper mapper;
    private final Clock clock;

    public TemplateService(WorkoutTemplateRepository templates, WorkoutTemplateExerciseRepository templateExercises,
                           ExerciseRepository exercises, TemplateMapper mapper, Clock clock) {
        this.templates = templates;
        this.templateExercises = templateExercises;
        this.exercises = exercises;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResponse<TemplateSummary> list(UUID userId, int page, int size) {
        Page<WorkoutTemplate> result = templates.findAllVisible(userId,
                PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id"))));
        List<UUID> ids = result.getContent().stream().map(WorkoutTemplate::getId).toList();
        Map<UUID, Long> counts = ids.isEmpty() ? Map.of()
                : templateExercises.findByTemplateIdInOrderByTemplateIdAscPositionAsc(ids).stream()
                        .collect(Collectors.groupingBy(WorkoutTemplateExercise::getTemplateId, Collectors.counting()));
        return PagedResponse.of(result, t -> mapper.toSummary(t, counts.getOrDefault(t.getId(), 0L).intValue()));
    }

    @Transactional(readOnly = true)
    public TemplateDetail get(UUID userId, UUID id) {
        return detail(requireVisible(userId, id));
    }

    @Transactional
    public TemplateDetail create(UUID userId, TemplateRequest request) {
        validateExercises(userId, request);
        WorkoutTemplate template = templates.save(new WorkoutTemplate(userId, request.name().trim(),
                blankToNull(request.notes()), Instant.now(clock)));
        saveExercises(template.getId(), request);
        return detail(template);
    }

    /** Replaces the name, notes and the whole ordered exercise list, all or nothing. */
    @Transactional
    public TemplateDetail replace(UUID userId, UUID id, TemplateRequest request) {
        WorkoutTemplate template = ownedForChange(userId, id);
        validateExercises(userId, request);

        template.update(request.name().trim(), blankToNull(request.notes()));
        // Flushes the name change, then deletes the old rows immediately, so re-adding an exercise
        // below cannot collide with its own old row.
        templateExercises.deleteByTemplateId(id);
        saveExercises(id, request);
        return detail(templates.findById(id).orElseThrow());
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        templates.delete(ownedForChange(userId, id)); // the database removes its exercise rows
    }

    /** Copies any visible template (built-in or mine) into the user's own templates. */
    @Transactional
    public TemplateDetail duplicate(UUID userId, UUID id) {
        WorkoutTemplate source = requireVisible(userId, id);
        String name = source.getName().length() + COPY_SUFFIX.length() > MAX_NAME
                ? source.getName().substring(0, MAX_NAME - COPY_SUFFIX.length()) + COPY_SUFFIX
                : source.getName() + COPY_SUFFIX;
        WorkoutTemplate copy = templates.save(new WorkoutTemplate(userId, name, source.getNotes(), Instant.now(clock)));

        List<WorkoutTemplateExercise> rows = templateExercises.findByTemplateIdOrderByPosition(id);
        Map<UUID, Exercise> byId = exercisesById(rows);
        int position = 1;
        for (WorkoutTemplateExercise row : rows) {
            if (byId.get(row.getExerciseId()).isArchived()) continue; // removed exercises are not carried over
            templateExercises.save(new WorkoutTemplateExercise(copy.getId(), row.getExerciseId(), position++, row.getTargetSets()));
        }
        return detail(copy);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private TemplateDetail detail(WorkoutTemplate template) {
        List<WorkoutTemplateExercise> rows = templateExercises.findByTemplateIdOrderByPosition(template.getId());
        return mapper.toDetail(template, rows, exercisesById(rows));
    }

    private Map<UUID, Exercise> exercisesById(List<WorkoutTemplateExercise> rows) {
        return exercises.findAllById(rows.stream().map(WorkoutTemplateExercise::getExerciseId).toList()).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
    }

    private void saveExercises(UUID templateId, TemplateRequest request) {
        int position = 1;
        for (TemplateExerciseInput input : request.exercises()) {
            templateExercises.save(new WorkoutTemplateExercise(templateId, input.exerciseId(), position++, input.targetSets()));
        }
    }

    /** Every exercise must be distinct, visible to the user, and not archived. */
    private void validateExercises(UUID userId, TemplateRequest request) {
        List<UUID> ids = request.exercises().stream().map(TemplateExerciseInput::exerciseId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_TEMPLATE_EXERCISE",
                    "An exercise can appear only once in a template");
        }
        Map<UUID, Exercise> visible = exercises.findVisibleByIds(ids, userId).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
        for (UUID id : ids) {
            Exercise exercise = visible.get(id);
            if (exercise == null) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Exercise not found");
            if (exercise.isArchived()) {
                throw new ApiException(HttpStatus.CONFLICT, "EXERCISE_ARCHIVED",
                        "\"" + exercise.getName() + "\" has been removed and cannot be used");
            }
        }
    }

    private WorkoutTemplate requireVisible(UUID userId, UUID id) {
        return templates.findVisible(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Template not found"));
    }

    private WorkoutTemplate ownedForChange(UUID userId, UUID id) {
        WorkoutTemplate template = requireVisible(userId, id);
        if (template.isBuiltIn()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BUILTIN_READ_ONLY",
                    "Built-in templates cannot be changed; duplicate it to make your own copy");
        }
        return template;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
