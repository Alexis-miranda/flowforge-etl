package com.flowforge.etl.cash.delivery;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashFrontCsvSerializerTest {

    @Test
    void shouldSerializeColumnsInConfiguredOrderAndProduceStableHash()
            throws Exception {
        List<?> records = List.of(
                Map.of(
                        "amount", "12.50",
                        "currency", "EUR",
                        "accountCode", "A1"
                )
        );

        var first = CashFrontCsvSerializer.serialize(
                records, "accountCode,currency,amount", ',');

        var second = CashFrontCsvSerializer.serialize(
                records, "accountCode,currency,amount", ',');

        String csv = new String(
                first.bytes(), StandardCharsets.UTF_8);

        assertEquals(
                "accountCode,currency,amount\r\nA1,EUR,12.50\r\n",
                csv);
        assertArrayEquals(first.bytes(), second.bytes());
        assertEquals(first.sha256(), second.sha256());

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(first.bytes());
        StringBuilder expectedHash = new StringBuilder();

        for (byte value : digest) {
            expectedHash.append(String.format("%02x", value & 0xff));
        }

        assertEquals(expectedHash.toString(), first.sha256());
        assertTrue(first.sha256().matches("[0-9a-f]{64}"));
    }

    @Test
    void shouldRejectAConfiguredColumnMissingFromARecord() {
        List<?> records = List.of(
                Map.of("accountCode", "A1", "currency", "EUR")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> CashFrontCsvSerializer.serialize(
                        records, "accountCode,currency,amount", ',')
        );
    }

    @Test
    void shouldRejectDuplicateConfiguredColumns() {
        List<?> records = List.of(
                Map.of("accountCode", "A1")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> CashFrontCsvSerializer.serialize(
                        records, "accountCode,accountCode", ',')
        );
    }
}