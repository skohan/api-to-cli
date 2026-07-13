package com.petstore.cli.output;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Renders a Jackson tree as a plain-text ASCII table. Handles the two response shapes the
 * CLI actually produces -- an array of objects, or a single object -- and falls back to a
 * one-line rendering for anything else (scalars, empty arrays, arrays of scalars).
 */
final class TableRenderer {

    private TableRenderer() {
    }

    static String render(JsonNode node) {
        if (node == null || node.isNull()) {
            return "(no result)";
        }
        if (node.isArray()) {
            return renderArray((ArrayNode) node);
        }
        if (node.isObject()) {
            return renderKeyValue((ObjectNode) node);
        }
        return node.asText();
    }

    private static String renderArray(ArrayNode array) {
        if (array.isEmpty()) {
            return "(no results)";
        }
        if (!array.get(0).isObject()) {
            // Array of scalars: one column, one row per element.
            List<String> rows = new ArrayList<>();
            array.forEach(element -> rows.add(cellText(element)));
            return new Grid(List.of("VALUE"), rows.stream().map(List::of).toList()).render();
        }

        Set<String> columns = new LinkedHashSet<>();
        array.forEach(element -> element.fieldNames().forEachRemaining(columns::add));

        List<List<String>> rows = new ArrayList<>();
        for (JsonNode element : array) {
            List<String> row = new ArrayList<>();
            for (String column : columns) {
                row.add(cellText(element.get(column)));
            }
            rows.add(row);
        }
        return new Grid(List.copyOf(columns), rows).render();
    }

    private static String renderKeyValue(ObjectNode object) {
        List<List<String>> rows = new ArrayList<>();
        object.fields().forEachRemaining(entry -> rows.add(List.of(entry.getKey(), cellText(entry.getValue()))));
        return new Grid(List.of("FIELD", "VALUE"), rows).render();
    }

    /** A node's display text within a table cell: scalars as-is, containers as compact JSON. */
    private static String cellText(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isValueNode()) {
            return value.asText();
        }
        return value.toString();
    }

    /** Column-aligned text grid with a header row and a '-' separator. */
    private record Grid(List<String> headers, List<List<String>> rows) {

        String render() {
            int[] widths = new int[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                widths[i] = headers.get(i).length();
            }
            for (List<String> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    widths[i] = Math.max(widths[i], row.get(i).length());
                }
            }

            StringBuilder sb = new StringBuilder();
            appendRow(sb, headers, widths);
            appendSeparator(sb, widths);
            for (List<String> row : rows) {
                appendRow(sb, row, widths);
            }
            // Drop the trailing newline; System.out.println adds its own.
            return sb.substring(0, sb.length() - 1);
        }

        private static void appendRow(StringBuilder sb, List<String> cells, int[] widths) {
            for (int i = 0; i < widths.length; i++) {
                String cell = i < cells.size() ? cells.get(i) : "";
                sb.append(pad(cell, widths[i]));
                if (i < widths.length - 1) {
                    sb.append("  ");
                }
            }
            sb.append('\n');
        }

        private static void appendSeparator(StringBuilder sb, int[] widths) {
            for (int i = 0; i < widths.length; i++) {
                sb.append("-".repeat(widths[i]));
                if (i < widths.length - 1) {
                    sb.append("  ");
                }
            }
            sb.append('\n');
        }

        private static String pad(String value, int width) {
            return value + " ".repeat(width - value.length());
        }
    }
}
