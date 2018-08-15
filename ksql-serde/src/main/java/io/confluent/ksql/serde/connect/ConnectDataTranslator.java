/**
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package io.confluent.ksql.serde.connect;

import io.confluent.ksql.GenericRow;
import io.confluent.ksql.util.KsqlException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.kafka.connect.data.Date;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Time;
import org.apache.kafka.connect.data.Timestamp;
import org.apache.kafka.connect.errors.DataException;

public class ConnectDataTranslator implements DataTranslator {
  private static final String PATH_SEPARATOR = "->";

  private final Schema schema;

  public ConnectDataTranslator(final Schema schema) {
    this.schema = schema;
  }

  @Override
  public GenericRow toKsqlRow(final Schema connectSchema,
                              final Object connectData) {
    if (!schema.type().equals(Schema.Type.STRUCT)) {
      throw new KsqlException("Schema for a KSQL row should be a struct");
    }

    final Struct rowStruct = (Struct) toKsqlValue(schema, connectSchema, connectData, "");
    if (rowStruct == null) {
      return null;
    }

    final List<Object> fields = new ArrayList<>(schema.fields().size());
    for (final Field field : schema.fields()) {
      fields.add(rowStruct.get(field.name()));
    }
    return new GenericRow(fields);
  }

  private RuntimeException throwTypeMismatchException(final String pathStr,
                                                      final Schema schema,
                                                      final Schema connectSchema) {
    throw new DataException(
        String.format(
            "Cannot deserialize type %s as type %s for field %s",
            connectSchema.type().getName(),
            schema.type().getName(),
            pathStr));
  }

  private void validateType(final String pathStr,
                            final Schema schema,
                            final Schema connectSchema,
                            final Schema.Type[] validTypes) {
    for (int i = 0; i < validTypes.length; i++) {
      if (connectSchema.type().equals(validTypes[i])) {
        return;
      }
    }
    throwTypeMismatchException(pathStr, schema, connectSchema);
  }

  private void validateType(final String pathStr,
                            final Schema schema,
                            final Schema connectSchema) {
    if (!connectSchema.type().equals(schema.type())) {
      throwTypeMismatchException(pathStr, schema, connectSchema);
    }
  }

  private static final Schema.Type[] STRING_ACCEPTABLE_TYPES = {
      Schema.Type.INT8,
      Schema.Type.INT16,
      Schema.Type.INT32,
      Schema.Type.INT64,
      Schema.Type.BOOLEAN,
      Schema.Type.STRING
  };

  private static final Schema.Type[] INT64_ACCEPTABLE_TYPES = {
      Schema.Type.INT64,
      Schema.Type.INT32,
      Schema.Type.INT16,
      Schema.Type.INT8
  };

  private static final Schema.Type[] INT32_ACCEPTABLE_TYPES = {
      Schema.Type.INT32,
      Schema.Type.INT16,
      Schema.Type.INT8
  };

  private static final Schema.Type[] FLOAT64_ACCEPTABLE_TYPES = {
      Schema.Type.FLOAT32,
      Schema.Type.FLOAT64
  };

  private void validateSchema(final String pathStr,
                              final Schema schema,
                              final Schema connectSchema) {
    switch (schema.type()) {
      case BOOLEAN:
      case ARRAY:
      case MAP:
      case STRUCT:
        validateType(pathStr, schema, connectSchema);
        break;
      case STRING:
        validateType(pathStr, schema, connectSchema, STRING_ACCEPTABLE_TYPES);
        break;
      case INT64:
        validateType(pathStr, schema, connectSchema, INT64_ACCEPTABLE_TYPES);
        break;
      case INT32:
        validateType(pathStr, schema, connectSchema, INT32_ACCEPTABLE_TYPES);
        break;
      case FLOAT64:
        validateType(pathStr, schema, connectSchema, FLOAT64_ACCEPTABLE_TYPES);
        break;
      default:
        throw new RuntimeException(
            "Unexpected data type seen in schema: " + schema.type().getName());
    }
  }

  private Object maybeConvertLogicalType(final Schema connectSchema, final Object connectValue) {
    if (connectSchema.name() == null) {
      return connectValue;
    }
    switch  (connectSchema.name()) {
      case Date.LOGICAL_NAME:
        return Date.fromLogical(connectSchema, (java.util.Date) connectValue);
      case Time.LOGICAL_NAME:
        return Time.fromLogical(connectSchema, (java.util.Date) connectValue);
      case Timestamp.LOGICAL_NAME:
        return Timestamp.fromLogical(connectSchema, (java.util.Date) connectValue);
      default:
        return connectValue;
    }
  }

  @SuppressWarnings("unchecked")
  private Object toKsqlValue(final Schema schema,
                             final Schema connectSchema,
                             final Object connectValue,
                             final String pathStr) {
    // Map a connect value+schema onto the schema expected by KSQL. For now this involves:
    // - handling case insensitivity for struct field names
    // - setting missing values to null
    if (connectSchema == null) {
      return null;
    }
    validateSchema(pathStr, schema, connectSchema);
    if (connectValue == null) {
      return null;
    }
    final Object convertedValue = maybeConvertLogicalType(connectSchema, connectValue);
    switch (schema.type()) {
      case INT64:
        return ((Number) convertedValue).longValue();
      case INT32:
        return ((Number) convertedValue).intValue();
      case FLOAT64:
        return ((Number) convertedValue).doubleValue();
      case ARRAY:
        return toKsqlArray(
            schema.valueSchema(), connectSchema.valueSchema(), (List) convertedValue, pathStr);
      case MAP:
        return toKsqlMap(
            schema.keySchema(), connectSchema.keySchema(),
            schema.valueSchema(), connectSchema.valueSchema(), (Map) convertedValue, pathStr);
      case STRUCT:
        return toKsqlStruct(schema, connectSchema, (Struct) convertedValue, pathStr);
      case STRING:
        // use String.valueOf to convert various int types and Boolean to string
        return String.valueOf(convertedValue);
      default:
        return convertedValue;
    }
  }

  private List toKsqlArray(final Schema valueSchema, final Schema connectValueSchema,
                           final List<Object> connectArray, final String pathStr) {
    return connectArray.stream()
        .map(o -> toKsqlValue(
            valueSchema, connectValueSchema, o, pathStr + PATH_SEPARATOR + "ARRAY"))
        .collect(Collectors.toList());
  }

  private Map toKsqlMap(final Schema keySchema, final Schema connectKeySchema,
                        final Schema valueSchema, final Schema connectValueSchema,
                        final Map<String, Object> connectMap, final String pathStr) {
    return connectMap.entrySet().stream()
        .collect(
            Collectors.toMap(
                e -> toKsqlValue(
                    keySchema,
                    connectKeySchema,
                    e.getKey(),
                    pathStr + PATH_SEPARATOR + "MAP_KEY"),
                e -> toKsqlValue(
                    valueSchema,
                    connectValueSchema,
                    e.getValue(),
                    pathStr + PATH_SEPARATOR + "MAP_VAL")
            ));
  }

  private Struct toKsqlStruct(final Schema schema,
                              final Schema connectSchema,
                              final Struct connectStruct,
                              final String pathStr) {
    // todo: check name here? e.g. what if the struct gets changed to a union?
    final Struct ksqlStruct = new Struct(schema);
    final Map<String, String> caseInsensitiveFieldNameMap
        = getCaseInsensitiveFieldMap(connectStruct.schema());
    for (final Field field : schema.fields()) {
      final String fieldNameUppercase = field.name().toUpperCase();
      // TODO: should we throw an exception if this is not true? this means the schema changed
      //       or the user declared the source with a schema incompatible with the registry schema
      if (caseInsensitiveFieldNameMap.containsKey(fieldNameUppercase)) {
        final Object fieldValue = connectStruct.get(
            caseInsensitiveFieldNameMap.get(fieldNameUppercase));
        final Schema fieldSchema = connectSchema.field(
            caseInsensitiveFieldNameMap.get(fieldNameUppercase)).schema();
        ksqlStruct.put(
            field.name(),
            toKsqlValue(
                field.schema(),
                fieldSchema,
                fieldValue,
                pathStr + PATH_SEPARATOR + field.name()));
      }
    }
    return ksqlStruct;
  }

  private Map<String, String> getCaseInsensitiveFieldMap(final Schema schema) {
    final Map<String, String> fieldNames = new HashMap<>();
    for (final Field field : schema.fields()) {
      fieldNames.put(field.name().toUpperCase(), field.name());
    }
    return fieldNames;
  }

  public Struct toConnectRow(final GenericRow row) {
    final Struct struct = new Struct(schema);
    for (int i = 0; i < schema.fields().size(); i++) {
      struct.put(schema.fields().get(i).name(), row.getColumns().get(i));
    }
    return struct;
  }
}
