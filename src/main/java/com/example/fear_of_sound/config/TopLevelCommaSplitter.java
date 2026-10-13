package com.example.fear_of_sound.config;

import java.util.ArrayList;
import java.util.List;

public final class TopLevelCommaSplitter {
    private TopLevelCommaSplitter() {}

    public static List<String> split(String input) {
        List<String> out = new ArrayList<>();
        if (input == null) return out;
        String s = input.trim();
        if (s.isEmpty()) return out;
        StringBuilder current = new StringBuilder();
        int brace = 0;
        int bracket = 0;
        boolean inQuotes = false;
        char quoteChar = '\0';
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuotes) {
                current.append(c);
                if (c == quoteChar) {
                    int backslashes = 0;
                    int j = i - 1;
                    while (j >= 0 && s.charAt(j) == '\\') { backslashes++; j--; }
                    if ((backslashes % 2) == 0) {
                        inQuotes = false;
                        quoteChar = '\0';
                    }
                }
                continue;
            }
            switch (c) {
                case '"':
                case '\'':
                    inQuotes = true; quoteChar = c; current.append(c); break;
                case '{': brace++; current.append(c); break;
                case '}': if (brace > 0) brace--; current.append(c); break;
                case '[': bracket++; current.append(c); break;
                case ']': if (bracket > 0) bracket--; current.append(c); break;
                case ',':
                    if (brace == 0 && bracket == 0) {
                        String token = current.toString().trim();
                        if (!token.isEmpty()) out.add(token);
                        current.setLength(0);
                    } else {
                        current.append(c);
                    }
                    break;
                default:
                    current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) out.add(last);
        return out;
    }
}
