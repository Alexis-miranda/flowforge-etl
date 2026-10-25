package com.flowforge.etl.connectors.file.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CashFrontCsvSerializer {

    private CashFrontCsvSerializer() {
    }

    public static SerializedCsv serialize(
            List<?> records,
            String configuredColumns,
            char delimiter) {

        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot write FRONT CSV without records");
        }

        List<String> columns = parseColumns(configuredColumns);
        List<Map<String, String>> validated = validateRecords(
                records, columns);

        CSVFormat format = CSVFormat.RFC4180.builder()
                .setDelimiter(delimiter)
                .setHeader(columns.toArray(String[]::new))
                .get();

        try (StringWriter writer = new StringWriter();
             CSVPrinter printer = new CSVPrinter(writer, format)) {

            for (Map<String, String> record : validated) {
                for (String column : columns) {
                    printer.print(record.get(column));
                }
                printer.println();
            }

            byte[] bytes = writer.toString()
                    .getBytes(StandardCharsets.UTF_8);

            return new SerializedCsv(bytes, sha256(bytes));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to serialize FRONT CSV", exception);
        }
    }

    private static List<String> parseColumns(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "At least one FRONT output column is required");
        }

        List<String> columns = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String item : value.split(",", -1)) {
            String column = item.trim();

            if (column.isEmpty()) {
                throw new IllegalArgumentException(
                        "FRONT columns must not contain blank names");
            }

            if (!seen.add(column)) {
                throw new IllegalArgumentException(
                        "Duplicate FRONT output column: " + column);
            }

            columns.add(column);
        }

        return List.copyOf(columns);
    }

    private static List<Map<String, String>> validateRecords(
            List<?> records,
            List<String> columns) {

        List<Map<String, String>> result = new ArrayList<>();

        for (Object record : records) {
            if (!(record instanceof Map<?, ?> row)) {
                throw new IllegalArgumentException(
                        "Each input record must be a map");
            }

            Map<String, String> converted = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : row.entrySet()) {
                if (!(entry.getKey() instanceof String key)
                        || !(entry.getValue() instanceof String value)) {
                    throw new IllegalArgumentException(
                            "Record keys and values must be strings");
                }
                converted.put(key, value);
            }

            for (String column : columns) {
                if (!converted.containsKey(column)) {
                    throw new IllegalArgumentException(
                            "Missing FRONT output column: " + column);
                }
            }

            result.add(Map.copyOf(converted));
        }

        return List.copyOf(result);
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(bytes);
            StringBuilder result = new StringBuilder(64);

            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }

            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    public record SerializedCsv(byte[] bytes, String sha256) {
        public SerializedCsv {
            bytes = bytes.clone();

            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "Invalid SHA-256 digest");
            }
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
