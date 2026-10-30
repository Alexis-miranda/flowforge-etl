package com.flowforge.etl.core.transform;

import java.util.Map;
import java.util.function.BiConsumer;

final class RecordMapper {

    private RecordMapper() {
    }

    static void forEachStringEntry(
            Map<?, ?> record,
            BiConsumer<String, String> consumer
    ) {
        for (Map.Entry<?, ?> entry : record.entrySet()) {
            if (!(entry.getKey() instanceof String key)
                    || !(entry.getValue() instanceof String value)) {
                throw new IllegalArgumentException(
                        "Record keys and values must be strings");
            }

            consumer.accept(key, value);
        }
    }
}
