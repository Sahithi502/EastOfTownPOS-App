package com.eastoftown.pos;

import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController @RequestMapping("/api/reports")
public class ReportController {
    final ReportService reports;final ReportExport exports;final AuthService auth;
    public ReportController(ReportService reports,ReportExport exports,AuthService auth){this.reports=reports;this.exports=exports;this.auth=auth;}
    ReportService.Range range(String preset,LocalDate from,LocalDate to){return ReportService.range(preset,from,to,LocalDate.now(ReportService.ZONE));}
    @GetMapping("/dashboard") public Object dashboard(@RequestParam(defaultValue="today") String preset,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){auth.admin();return reports.dashboard(range(preset,from,to));}
    @GetMapping("/{type}") public Object report(@PathVariable String type,@RequestParam(defaultValue="today") String preset,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) String customerId){auth.admin();return reports.report(type,range(preset,from,to),customerId);}
    @GetMapping("/{type}/export") public ResponseEntity<byte[]> export(@PathVariable String type,@RequestParam String format,@RequestParam(defaultValue="today") String preset,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) String customerId){auth.admin();if(!format.equals("pdf")&&!format.equals("xlsx"))throw new IllegalArgumentException("Choose PDF or XLSX");var report=reports.report(type,range(preset,from,to),customerId);return ResponseEntity.ok().contentType(MediaType.parseMediaType(format.equals("pdf")?"application/pdf":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(format.equals("pdf")?exports.pdf(report):exports.excel(report));}
}
