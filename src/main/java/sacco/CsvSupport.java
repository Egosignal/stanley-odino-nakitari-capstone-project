package sacco;

import java.util.ArrayList;
import java.util.List;

/** Small CSV helpers for the simple members.csv file used by this project. */
final class CsvSupport {
    private CsvSupport() { }

    static String row(String... values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(',');
            String value = values[i] == null ? "" : values[i];
            result.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        return result.toString();
    }

    static List<String> parse(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (quoted) {
                if (current == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(current);
                }
            } else if (current == ',' ) {
                fields.add(field.toString());
                field.setLength(0);
            } else if (current == '"' && field.length() == 0) {
                quoted = true;
            } else {
                field.append(current);
            }
        }

        if (quoted) throw new IllegalArgumentException("Unclosed quoted CSV field");
        fields.add(field.toString());
        return fields;
    }
}
