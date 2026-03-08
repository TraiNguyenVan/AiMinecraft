package com.yourname.aiminecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LogUtils {
    public static String getAllLines(File file) {
        if (!file.exists()) return "(No previous knowledge found)";
        
        try {
            return java.nio.file.Files.readString(file.toPath());
        } catch (IOException e) {
            return "(Error reading brain logs)";
        }
    }
}
