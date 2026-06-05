package com.workflow.ejecucion.dto;

import com.workflow.formulario.model.Formulario.CampoFormulario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VistaFuncionarioResponse {
    private String ejecucionId;
    private String tramiteId;
    private String departamentoId;
    private String clienteId;
    private String empresaId;
    private String fase;
    private List<CampoConValor> camposCliente;
    private List<CampoFormulario> camposFuncionario;
}
