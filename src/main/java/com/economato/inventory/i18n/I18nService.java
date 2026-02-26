package com.economato.inventory.i18n;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class I18nService {

    private final MessageSource messageSource;

    public String getMessage(MessageKey messageKey) {
        return messageSource.getMessage(messageKey.getKey(), null, LocaleContextHolder.getLocale());
    }

    public String getMessage(MessageKey messageKey, Object... args) {
        return messageSource.getMessage(messageKey.getKey(), args, LocaleContextHolder.getLocale());
    }
}
