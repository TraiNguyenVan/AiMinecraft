package com.yourname.aiminecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LogUtils {
    public static String getLastLines(File file, int lineCount) {
        if (!file.exists()) return "(No previous knowledge found)";
        
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
                if (lines.size() > lineCount) {
                    lines.remove(0);
                }
            }
        } catch (IOException e) {
            return "(Error reading brain logs)";
        }
        return String.join("\n", lines);
    }
}
