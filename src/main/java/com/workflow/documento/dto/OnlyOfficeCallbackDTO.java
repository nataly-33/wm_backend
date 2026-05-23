package com.workflow.documento.dto;

import lombok.Data;
import java.util.List;

@Data
public class OnlyOfficeCallbackDTO {
    private int status;
    private String url;
    private String key;
    private List<String> users;
}
