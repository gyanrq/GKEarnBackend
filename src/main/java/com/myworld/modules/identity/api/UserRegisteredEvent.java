package com.myworld.modules.identity.api;

import com.myworld.modules.identity.domain.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserRegisteredEvent extends ApplicationEvent {
    private final User user;
    private final String referredByCode;

    public UserRegisteredEvent(Object source, User user, String referredByCode) {
        super(source);
        this.user = user;
        this.referredByCode = referredByCode;
    }
}
