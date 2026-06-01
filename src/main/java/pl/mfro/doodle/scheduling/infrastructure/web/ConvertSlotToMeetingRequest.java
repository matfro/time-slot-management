package pl.mfro.doodle.scheduling.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ConvertSlotToMeetingRequest(
        @NotBlank(message = "Title is required")
        String title,

        String description,

        @NotEmpty(message = "Meeting must have at least one participant")
        List<String> participants
) {}
