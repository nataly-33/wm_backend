package com.workflow.ejecucion.dto;

import com.workflow.formulario.model.Formulario.CampoFormulario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampoConValor {
    private CampoFormulario campo;
    private Object valor;
}
