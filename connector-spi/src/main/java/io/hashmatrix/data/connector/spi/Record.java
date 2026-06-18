package io.hashmatrix.data.connector.spi;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一行数据：列名 → 值（保序）。读写主链路的搬运单元。
 *
 * <p>实现 {@link Serializable}，便于作为 Flink 采集作业的流元素跨算子传递。值为
 * JDBC/驱动读出的原生 Java 对象（可为 {@code null}）。本类不可变，构造时拷贝入参。
 */
public final class Record implements Serializable {

    private static final long serialVersionUID = 1L;

    private final LinkedHashMap<String, Object> values;

    private Record(LinkedHashMap<String, Object> values) {
        this.values = values;
    }

    /** 由保序 map 构造（防御性拷贝）。 */
    public static Record of(Map<String, Object> values) {
        return new Record(new LinkedHashMap<>(Objects.requireNonNull(values, "values")));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 取列值（可能为 {@code null}）。 */
    public Object get(String column) {
        return values.get(column);
    }

    public boolean has(String column) {
        return values.containsKey(column);
    }

    /** 列名集合（保序，只读）。 */
    public Iterable<String> columns() {
        return Collections.unmodifiableSet(values.keySet());
    }

    /** 列名→值的只读视图（保序）。 */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }

    public int size() {
        return values.size();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Record other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "Record" + values;
    }

    /** {@link Record} 构造器（保序追加列）。 */
    public static final class Builder {
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        public Builder set(String column, Object value) {
            values.put(Objects.requireNonNull(column, "column"), value);
            return this;
        }

        public Record build() {
            return new Record(new LinkedHashMap<>(values));
        }
    }
}
