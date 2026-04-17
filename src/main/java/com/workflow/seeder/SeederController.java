package com.workflow.seeder;

import com.workflow.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seeder")
@RequiredArgsConstructor
public class SeederController {
    private final SeederService seederService;

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<String>> runSeeder() {
        return ResponseEntity.ok(ApiResponse.success("Seeder ejecutado con éxito", seederService.seedAll()));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<String>> clearSeeder() {
        return ResponseEntity.ok(ApiResponse.success("Datos eliminados correctamente", seederService.clearAll()));
    }
}
