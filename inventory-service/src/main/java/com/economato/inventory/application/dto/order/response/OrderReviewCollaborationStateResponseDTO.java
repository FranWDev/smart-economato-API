package com.economato.inventory.application.dto.order.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReviewCollaborationStateResponseDTO {
    private Integer orderId;
    private boolean locked;
    private Integer lockedByUserId;
    private String lockedByUsername;
    private String lockedByDisplayName;
    private boolean currentUserOwner;
    private boolean currentUserAdmin;
    private boolean currentUserCollaborator;
    private boolean currentUserCanAdmit;
    private List<OrderCollaborationUserResponseDTO> collaborators;
    private List<OrderCollaborationUserResponseDTO> pendingRequests;
    private List<OrderCollaborationFieldLockResponseDTO> fieldLocks;
    private Map<String, Object> fieldValues;
}
