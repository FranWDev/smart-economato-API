package com.economato.inventory.infrastructure.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador manual para servir la documentación de la API con Scalar.
 * Se utiliza como alternativa a la auto-configuración cuando esta falla
 * en entornos de contenedores o versiones específicas de Spring Boot.
 */
@Controller
@Tag(name = "Documentación", description = "Endpoints para la visualización de la documentación de la API")
public class ScalarController {

    @Value("${scalar.url:/v3/api-docs}")
    private String openApiUrl;

    @Value("${scalar.theme:deepSpace}")
    private String theme;

    @Value("${scalar.darkMode:true}")
    private boolean darkMode;

    @Value("${scalar.hideTestRequestButton:false}")
    private boolean hideTestRequestButton;

    @Value("${scalar.hideModels:false}")
    private boolean hideModels;

    @Value("${scalar.pageTitle:Smart Economato | API Docs}")
    private String pageTitle;

    @GetMapping({"/scalar", "/scalar/"})
    @ResponseBody
    @Operation(hidden = true)
    public String scalarDocs() {
        return """
            <!DOCTYPE html>
            <html>
              <head>
                <title>%s</title>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <style>
                  body {
                    margin: 0;
                  }
                </style>
              </head>
              <body>
                <script
                  id="api-reference"
                  data-url="%s"
                  data-configuration='%s'
                ></script>
                <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
              </body>
            </html>
            """.formatted(
                pageTitle,
                openApiUrl,
                generateConfiguration()
            );
    }

    private String generateConfiguration() {
        return String.format(
            "{\"theme\": \"%s\", \"darkMode\": %b, \"hideTestRequestButton\": %b, \"hideModels\": %b}",
            theme, darkMode, hideTestRequestButton, hideModels
        );
    }
}
