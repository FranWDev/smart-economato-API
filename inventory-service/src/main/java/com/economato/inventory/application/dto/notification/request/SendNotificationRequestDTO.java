package com.economato.inventory.application.dto.notification.request;

import com.economato.inventory.domain.model.user.Role;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequestDTO {

    @NotBlank
    private String title;

    @NotBlank
    private String message;

    private List<Integer> recipientIds;

    private Role targetRole;
}
