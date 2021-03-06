/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.extensions.sql.meta.provider.pubsub;

import static org.apache.beam.vendor.guava.v26_0_jre.com.google.common.base.Preconditions.checkArgument;

import java.nio.charset.StandardCharsets;
import org.apache.beam.sdk.annotations.Experimental;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.schemas.transforms.DropFields;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ToJson;
import org.apache.beam.sdk.transforms.WithTimestamps;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.collect.ImmutableMap;

/**
 * A {@link PTransform} to convert {@link Row} to {@link PubsubMessage} with JSON payload.
 *
 * <p>Currently only supports writing a flat schema into a JSON payload. This means that all Row
 * field values are written to the {@link PubsubMessage} JSON payload, except for {@code
 * event_timestamp}, which is either ignored or written to the message attributes, depending on
 * whether {@link PubsubJsonTableProvider.PubsubIOTableConfiguration#getTimestampAttribute()} is
 * set.
 */
@Experimental
public class RowToPubsubMessage extends PTransform<PCollection<Row>, PCollection<PubsubMessage>> {
  private final PubsubJsonTableProvider.PubsubIOTableConfiguration config;

  private RowToPubsubMessage(PubsubJsonTableProvider.PubsubIOTableConfiguration config) {
    checkArgument(
        config.getUseFlatSchema(), "RowToPubsubMessage is only supported for flattened schemas.");

    this.config = config;
  }

  public static RowToPubsubMessage fromTableConfig(
      PubsubJsonTableProvider.PubsubIOTableConfiguration config) {
    return new RowToPubsubMessage(config);
  }

  @Override
  public PCollection<PubsubMessage> expand(PCollection<Row> input) {
    PCollection<Row> withTimestamp =
        (config.useTimestampAttribute())
            ? input.apply(
                WithTimestamps.of((row) -> row.getDateTime("event_timestamp").toInstant()))
            : input;

    return withTimestamp
        .apply(DropFields.fields("event_timestamp"))
        .apply(ToJson.of())
        .apply(
            MapElements.into(TypeDescriptor.of(PubsubMessage.class))
                .via(
                    (String json) ->
                        new PubsubMessage(
                            json.getBytes(StandardCharsets.ISO_8859_1), ImmutableMap.of())));
  }
}
