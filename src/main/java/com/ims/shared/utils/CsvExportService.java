package com.ims.shared.utils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CsvExportService {

    public String exportToCsv(List<String> headers, List<Map<String, Object>> data) {
        StringBuilder csv = new StringBuilder();
        
        // Add headers
        csv.append(String.join(",", headers)).append("\n");
        
        // Add data rows
        for (Map<String, Object> row : data) {
            String line = headers.stream()
                .map(header -> {
                    Object value = row.get(header);
                    if (value == null) return "";
                    String strValue = value.toString().replace("\"", "\"\"");
                    if (strValue.contains(",") || strValue.contains("\n") || strValue.contains("\"")) {
                        return "\"" + strValue + "\"";
                    }
                    return strValue;
                })
                .collect(Collectors.joining(","));
            csv.append(line).append("\n");
        }
        
        return csv.toString();
    }
}
