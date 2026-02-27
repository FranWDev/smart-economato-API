package com.economato.inventory.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Arrays;
import java.util.Locale;

/**
 * Configuración central de Internacionalización (i18n).
 * - Registra el MessageSource para las anotaciones @Valid
 * (LocalValidatorFactoryBean)
 * - Configura el LocaleResolver para extraer automáticamente el idioma del
 * navegador HTTP (Accept-Language)
 */
@Configuration
public class I18nValidationConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        // Definimos español como idioma por defecto (fallback absoluto)
        resolver.setDefaultLocale(Locale.forLanguageTag("es"));
        // Restringimos los lenguajes soportados a los que tenemos .properties
        resolver.setSupportedLocales(Arrays.asList(
                Locale.forLanguageTag("es"),
                Locale.forLanguageTag("en"),
                Locale.forLanguageTag("de"),
                Locale.forLanguageTag("fr"),
                Locale.forLanguageTag("it"),
                Locale.forLanguageTag("pt"),
                Locale.forLanguageTag("ca"),
                Locale.forLanguageTag("eu"),
                Locale.forLanguageTag("gl"),
                Locale.forLanguageTag("nl"),
                Locale.forLanguageTag("sv"),
                Locale.forLanguageTag("no"),
                Locale.forLanguageTag("da"),
                Locale.forLanguageTag("fi"),
                Locale.forLanguageTag("pl"),
                Locale.forLanguageTag("cs"),
                Locale.forLanguageTag("sk"),
                Locale.forLanguageTag("hu"),
                Locale.forLanguageTag("ro"),
                Locale.forLanguageTag("hr"),
                Locale.forLanguageTag("sl"),
                Locale.forLanguageTag("bg"),
                Locale.forLanguageTag("is"),
                Locale.forLanguageTag("et"),
                Locale.forLanguageTag("lv"),
                Locale.forLanguageTag("lt"),
                Locale.forLanguageTag("el"),
                Locale.forLanguageTag("mt"),
                Locale.forLanguageTag("ga"),
                Locale.forLanguageTag("tr"),
                Locale.forLanguageTag("ru"),
                Locale.forLanguageTag("uk"),
                Locale.forLanguageTag("ja"),
                Locale.forLanguageTag("zh"),
                Locale.forLanguageTag("eo")));
        return resolver;
    }

    @Bean
    public LocalValidatorFactoryBean getValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
        bean.setValidationMessageSource(messageSource);
        return bean;
    }

}
