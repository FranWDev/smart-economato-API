package com.economato.inventory.service;

import com.economato.inventory.dto.response.AllergenResponseDTO;
import com.economato.inventory.dto.response.RecipeComponentResponseDTO;
import com.economato.inventory.dto.response.RecipeResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RecipePdfServiceTest {

    @InjectMocks
    private RecipePdfService recipePdfService;

    private RecipeResponseDTO testRecipe;

    @BeforeEach
    void setUp() {
        testRecipe = new RecipeResponseDTO();
        testRecipe.setId(1);
        testRecipe.setName("Paella Valenciana");
        testRecipe.setPresentation("Plato tradicional valenciano servido en paellera.");
        testRecipe.setElaboration("1. Calentar aceite en la paellera\n2. Sofrir el pollo y las verduras\n3. Añadir el arroz y tostar\n4. Agregar el caldo caliente\n5. Cocinar a fuego medio durante 18 minutos");
        testRecipe.setTotalCost(new BigDecimal("15.50"));

        List<RecipeComponentResponseDTO> components = new ArrayList<>();
        
        RecipeComponentResponseDTO component1 = new RecipeComponentResponseDTO();
        component1.setId(1);
        component1.setProductId(1);
        component1.setProductName("Arroz");
        component1.setQuantity(new BigDecimal("0.400"));
        component1.setSubtotal(new BigDecimal("2.00"));
        components.add(component1);

        RecipeComponentResponseDTO component2 = new RecipeComponentResponseDTO();
        component2.setId(2);
        component2.setProductId(2);
        component2.setProductName("Pollo");
        component2.setQuantity(new BigDecimal("0.500"));
        component2.setSubtotal(new BigDecimal("8.00"));
        components.add(component2);

        RecipeComponentResponseDTO component3 = new RecipeComponentResponseDTO();
        component3.setId(3);
        component3.setProductId(3);
        component3.setProductName("Pimiento");
        component3.setQuantity(new BigDecimal("0.200"));
        component3.setSubtotal(new BigDecimal("1.50"));
        components.add(component3);

        testRecipe.setComponents(components);

        List<AllergenResponseDTO> allergens = new ArrayList<>();
        AllergenResponseDTO allergen = new AllergenResponseDTO();
        allergen.setId(1);
        allergen.setName("Gluten");
        allergens.add(allergen);
        
        testRecipe.setAllergens(allergens);
    }

    @Test
    void generateRecipePdf_WithCompleteRecipe_ShouldGeneratePdf() throws Exception {
        // When
        byte[] pdfBytes = recipePdfService.generateRecipePdf(testRecipe);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        
        // Verificar que comienza con la firma de PDF
        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, 4));
        assertEquals("%PDF", pdfHeader);
    }

    @Test
    void generateRecipePdf_WithMinimalRecipe_ShouldGeneratePdf() throws Exception {
        // Given - Receta mínima sin presentación, elaboración ni alérgenos
        RecipeResponseDTO minimalRecipe = new RecipeResponseDTO();
        minimalRecipe.setId(2);
        minimalRecipe.setName("Receta Simple");
        minimalRecipe.setTotalCost(new BigDecimal("5.00"));
        minimalRecipe.setComponents(new ArrayList<>());
        minimalRecipe.setAllergens(new ArrayList<>());

        // When
        byte[] pdfBytes = recipePdfService.generateRecipePdf(minimalRecipe);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        
        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, 4));
        assertEquals("%PDF", pdfHeader);
    }

    @Test
    void generateRecipePdf_WithEmptyAllergens_ShouldGeneratePdf() throws Exception {
        // Given
        testRecipe.setAllergens(new ArrayList<>());

        // When
        byte[] pdfBytes = recipePdfService.generateRecipePdf(testRecipe);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateRecipePdf_WithNullElaboration_ShouldGeneratePdf() throws Exception {
        // Given
        testRecipe.setElaboration(null);

        // When
        byte[] pdfBytes = recipePdfService.generateRecipePdf(testRecipe);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateRecipePdf_WithMultipleAllergens_ShouldGeneratePdf() throws Exception {
        // Given
        List<AllergenResponseDTO> allergens = new ArrayList<>();
        
        AllergenResponseDTO allergen1 = new AllergenResponseDTO();
        allergen1.setId(1);
        allergen1.setName("Gluten");
        allergens.add(allergen1);
        
        AllergenResponseDTO allergen2 = new AllergenResponseDTO();
        allergen2.setId(2);
        allergen2.setName("Lactosa");
        allergens.add(allergen2);
        
        AllergenResponseDTO allergen3 = new AllergenResponseDTO();
        allergen3.setId(3);
        allergen3.setName("Frutos secos");
        allergens.add(allergen3);
        
        testRecipe.setAllergens(allergens);

        // When
        byte[] pdfBytes = recipePdfService.generateRecipePdf(testRecipe);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateRecipePdf_WithLongRecipeName_ShouldGeneratePdf() throws Exception {
        // Given
        testRecipe.setName("Receta con un nombre extremadamente largo que podría causar problemas de renderizado en el PDF");

        // When
        byte[] pdfBytes = recipePdfService.generateRecipePdf(testRecipe);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateRecipePdf_WithSpecialCharacters_ShouldGeneratePdf() throws Exception {
        // Given
        testRecipe.setName("Paella Española con Ñ y acentos: áéíóú ÁÉÍÓÚ");
        testRecipe.setPresentation("Descripción con caracteres especiales: €, %, &, @");

        // When
        byte[] pdfBytes = recipePdfService.generateRecipePdf(testRecipe);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateRecipePdf_WithZeroCost_ShouldGeneratePdf() throws Exception {
        // Given
        testRecipe.setTotalCost(BigDecimal.ZERO);

        // When
        byte[] pdfBytes = recipePdfService.generateRecipePdf(testRecipe);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateRecipePdf_WithManyComponents_ShouldGeneratePdf() throws Exception {
        // Given - Agregar muchos componentes
        List<RecipeComponentResponseDTO> components = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            RecipeComponentResponseDTO component = new RecipeComponentResponseDTO();
            component.setId(i);
            component.setProductId(i);
            component.setProductName("Ingrediente " + i);
            component.setQuantity(new BigDecimal("0.100"));
            component.setSubtotal(new BigDecimal("1.50"));
            components.add(component);
        }
        testRecipe.setComponents(components);

        // When
        byte[] pdfBytes = recipePdfService.generateRecipePdf(testRecipe);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void generateRecipePdf_WithLongElaboration_ShouldGeneratePdf() throws Exception {
        // Given
        StringBuilder longElaboration = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            longElaboration.append(i).append(". Este es un paso muy detallado de la elaboración que describe minuciosamente todo el proceso de preparación.\n");
        }
        testRecipe.setElaboration(longElaboration.toString());

        // When
        byte[] pdfBytes = recipePdfService.generateRecipePdf(testRecipe);

        // Then
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }
}
