/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.glutenproject.memory;

import io.glutenproject.proto.MemoryUsageStats;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

// thread safe
public class SimpleMemoryUsageRecorder implements MemoryUsageStatsBuilder {
  private final AtomicLong peak = new AtomicLong(0L);
  private final AtomicLong current = new AtomicLong(0L);

  public void inc(long bytes) {
    final long total = this.current.addAndGet(bytes);
    long prev_peak;
    do {
      prev_peak = this.peak.get();
      if (total <= prev_peak) {
        break;
      }
    } while (!this.peak.compareAndSet(prev_peak, total));
  }

  // peak used bytes
  public long peak() {
    return peak.get();
  }

  // current used bytes
  public long current() {
    return current.get();
  }

  public MemoryUsageStats toStats() {
    return MemoryUsageStats.newBuilder()
        .setPeak(peak.get())
        .setCurrent(current.get())
        .putAllChildren(Collections.emptyMap())
        .build();
  }
}
