## Contexto

El proyecto es `FranWDev/smart-economato-API`, un sistema de gestión de inventario para escuelas culinarias. Se necesita añadir un campo `lotQuantity` (cantidad por lote) al modelo de Producto. Este campo es nullable (no todos los productos lo necesitan) y representa la cantidad mínima de compra (ej: bote de 1L, saco de 1kg). El backend solo persiste y devuelve el valor; el frontend se encargará de la lógica de redondeo.

**IMPORTANTE**: El proyecto usa `spring.jpa.hibernate.ddl-auto=validate` (archivo `inventory-service/src/main/resources/application.properties` línea 44), así que Hibernate NO crea columnas automáticamente. Se necesita un script SQL.

## Pasos de implementación

### 1. Script SQL de migración

Crear un script SQL (o añadir al proceso de inicialización existente, ver `postgres-init.sh` en la raíz) para añadir la columna:

```sql
ALTER TABLE product ADD COLUMN IF NOT EXISTS lot_quantity NUMERIC(10,3) DEFAULT NULL;
```

### 2. Entidad Product (`inventory-service/src/main/java/com/economato/inventory/domain/model/Product.java`)

Añadir el campo después de `availabilityPercentage` (línea ~60):

```java
@DecimalMin(value = "0.001", message = "{validation.product.lotQuantity.decimalMin}")
@Digits(integer = 10, fraction = 3, message = "{validation.product.lotQuantity.digits}")
@Column(name = "lot_quantity", precision = 10, scale = 3)
private BigDecimal lotQuantity;
```

El campo es nullable (sin `@NotNull`) porque no todos los productos necesitan cantidad por lote.

### 3. ProductRequestDTO (`inventory-service/src/main/java/com/economato/inventory/application/dto/request/ProductRequestDTO.java`)

Añadir después de `availabilityPercentage` (línea ~51):

```java
@DecimalMin(value = "0.001", message = "{validation.productRequestDTO.lotQuantity.decimalMin}")
@Digits(integer = 10, fraction = 3, message = "{validation.productRequestDTO.lotQuantity.digits}")
@Schema(description = "Cantidad por lote de compra. Representa la unidad mínima de compra del producto (ej: 1.000 para botes de 1L, 5.000 para sacos de 5kg). Si se establece, el frontend puede redondear las cantidades de pedido al múltiplo superior de este valor.", example = "1.000")
private BigDecimal lotQuantity;
```

### 4. ProductResponseDTO (`inventory-service/src/main/java/com/economato/inventory/application/dto/response/ProductResponseDTO.java`)

Añadir después de `availabilityPercentage` (línea ~35):

```java
@Schema(description = "Cantidad por lote de compra. Null si no aplica.", example = "1.000")
private BigDecimal lotQuantity;
```

### 5. ProductProjection (`inventory-service/src/main/java/com/economato/inventory/application/dto/projection/ProductProjection.java`)

Añadir un nuevo getter en la interfaz (después de `getAvailabilityPercentage()`, línea ~19):

```java
BigDecimal getLotQuantity();
```

### 6. McpProductDto (`inventory-service/src/main/java/com/economato/inventory/application/dto/mcp/McpProductDto.java`)

Añadir campo:

```java
private BigDecimal lotQuantity;
```

### 7. McpProductDeepDto (`inventory-service/src/main/java/com/economato/inventory/application/dto/mcp/McpProductDeepDto.java`)

Añadir campo:

```java
private BigDecimal lotQuantity;
```

### 8. McpToolReadService (`inventory-service/src/main/java/com/economato/inventory/application/usecase/mcp/McpToolReadService.java`)

En el método `getProductDeep` (~línea 102-118), añadir `.lotQuantity(product.getLotQuantity())` al builder de `McpProductDeepDto`.

En el método `mapProduct` (~línea 360-368), añadir `.lotQuantity(product.getLotQuantity())` al builder de `McpProductDto`.

### 9. ProductMapper - NO requiere cambios

El mapper (`inventory-service/src/main/java/com/economato/inventory/application/mapper/ProductMapper.java`) usa MapStruct con `NullValuePropertyMappingStrategy.IGNORE` y mapea por convención de nombres. Al llamarse `lotQuantity` en todos los DTOs y la entidad, MapStruct lo resolverá automáticamente.

### 10. Archivos i18n 

Para el archivo base y `_es`:
```properties
validation.product.lotQuantity.decimalMin=La cantidad por lote debe ser mayor a 0
validation.product.lotQuantity.digits=Formato de cantidad por lote inválido
validation.productRequestDTO.lotQuantity.decimalMin=La cantidad por lote debe ser mayor que cero
validation.productRequestDTO.lotQuantity.digits=La cantidad por lote debe tener máximo 10 dígitos enteros y 3 decimales
```

Para `_en`:
```properties
validation.product.lotQuantity.decimalMin=Lot quantity must be greater than 0
validation.product.lotQuantity.digits=Invalid lot quantity format
validation.productRequestDTO.lotQuantity.decimalMin=Lot quantity must be greater than zero
validation.productRequestDTO.lotQuantity.digits=Lot quantity must have at most 10 integer digits and 3 decimal places
```

### 11. Tests

**TestDataUtil** (`inventory-service/src/test/java/com/economato/inventory/infrastructure/TestDataUtil.java`):
- En `createProduct` (~línea 70), no es necesario establecer `lotQuantity` ya que es nullable, pero se puede añadir un helper adicional `createProductWithLot` si se desea.
- En `createProductRequestDTO` (~línea 206), opcionalmente añadir `dto.setLotQuantity(new BigDecimal("1.000"));` para cubrir el campo.

**ProductServiceTest** (`inventory-service/src/test/java/com/economato/inventory/application/usecase/ProductServiceTest.java`):
- En el `setUp` (~línea 96-125), opcionalmente establecer `lotQuantity` en los objetos de test y en el mock de `testProjection` añadir: `lenient().when(testProjection.getLotQuantity()).thenReturn(new BigDecimal("1.000"));`

**McpToolReadServiceTest** (`inventory-service/src/test/java/com/economato/inventory/application/usecase/mcp/McpToolReadServiceTest.java`):
- Actualizar los tests que construyen `Product` para incluir `lotQuantity` si verifican los DTOs MCP.

### 12. Verificación final

Después de todos los cambios:

2. Compilar el proyecto con `mvn clean compile` para que MapStruct regenere los mappers.
3. Ejecutar los tests con `mvn test`.
4. Verificar que Hibernate `validate` no falle al arrancar la aplicación.