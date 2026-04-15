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

    @PostMapping("/day4/seed")
    public ResponseEntity<ApiResponse<String>> seedDia4() {
        return ResponseEntity.ok(ApiResponse.success("Seeder ejecutado", seederService.seedDia4()));
    }

    @PostMapping("/dia6")
    public ResponseEntity<ApiResponse<String>> seedDia6() {
        return ResponseEntity.ok(ApiResponse.success("Seeder dia 6 ejecutado", seederService.seedDia6()));
    }

    @DeleteMapping("/day4/clear")
    public ResponseEntity<ApiResponse<String>> clearDia4() {
        return ResponseEntity.ok(ApiResponse.success("Datos eliminados", seederService.clearAll()));
    }
}
