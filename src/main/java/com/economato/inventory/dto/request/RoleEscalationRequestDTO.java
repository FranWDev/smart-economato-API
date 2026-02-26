package com.economato.inventory.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleEscalationRequestDTO {

    @NotNull(message = "{roleescalationrequestdto.notnull.la.duraci.n.en.minutos.es.obli}")
    @Min(value = 1, message = "{roleescalationrequestdto.min.la.duraci.n.m.nima.debe.ser.de}")
    @Max(value = 1440, message = "{roleescalationrequestdto.max.la.duraci.n.m.xima.permitida.e}")
    private Integer durationMinutes;

}
