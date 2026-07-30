package com.economato.user.application.port.out;

import com.economato.user.application.dto.event.UserCreatedEvent;
import com.economato.user.application.dto.event.UserDeletedEvent;
import com.economato.user.application.dto.event.UserRoleChangedEvent;
import com.economato.user.application.dto.event.UserUpdatedEvent;

public interface UserEventPublisherPort {
    void publishUserCreated(UserCreatedEvent event);
    void publishUserUpdated(UserUpdatedEvent event);
    void publishUserDeleted(UserDeletedEvent event);
    void publishUserRoleChanged(UserRoleChangedEvent event);
}
