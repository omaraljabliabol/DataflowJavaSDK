/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.util;

import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.transforms.windowing.BoundedWindow;
import com.google.cloud.dataflow.sdk.values.CodedTupleTag;
import com.google.cloud.dataflow.sdk.values.CodedTupleTagMap;
import com.google.cloud.dataflow.sdk.values.PCollectionView;
import com.google.cloud.dataflow.sdk.values.TupleTag;

import org.joda.time.Instant;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Context about the current execution.  This is guaranteed to exist during processing,
 * but does not necessarily persist between different batches of work.
 */
public abstract class ExecutionContext {
  private Map<String, StepContext> cachedStepContexts = new HashMap<>();

  /**
   * Returns the {@link StepContext} associated with the given step.
   */
  public StepContext getStepContext(String stepName) {
    StepContext context = cachedStepContexts.get(stepName);
    if (context == null) {
      context = createStepContext(stepName);
      cachedStepContexts.put(stepName, context);
    }
    return context;
  }

  /**
   * Returns a collection view of all of the {@link StepContext}s.
   */
  public Collection<StepContext> getAllStepContexts() {
    return cachedStepContexts.values();
  }

  /**
   * Implementations should override this to create the specific type
   * of {@link StepContext} they neeed.
   */
  public abstract StepContext createStepContext(String stepName);

  /**
   * Return the {@link TimerManager} to use with this context, or null if it should be emulated.
   */
  public abstract TimerManager getTimerManager();

  /**
   * Hook for subclasses to implement that will be called whenever
   * {@link com.google.cloud.dataflow.sdk.transforms.DoFn.Context#output}
   * is called.
   */
  public void noteOutput(WindowedValue<?> output) {}

  /**
   * Hook for subclasses to implement that will be called whenever
   * {@link com.google.cloud.dataflow.sdk.transforms.DoFn.Context#sideOutput}
   * is called.
   */
  public void noteSideOutput(TupleTag<?> tag, WindowedValue<?> output) {}

  /**
   * Returns the side input associated with the given view and window, given the set of side
   * inputs available.
   */
  public abstract <T> T getSideInput(
      PCollectionView<T> view, BoundedWindow mainInputWindow, PTuple sideInputs);

  /**
   * Writes the given {@link PCollectionView} data to a globally accessible location.
   */
  public <T, W extends BoundedWindow> void writePCollectionViewData(
      TupleTag<?> tag,
      Iterable<WindowedValue<T>> data, Coder<Iterable<WindowedValue<T>>> dataCoder,
      W window, Coder<W> windowCoder) throws IOException {
    throw new UnsupportedOperationException("Not implemented.");
  }

  /**
   * Per-step, per-key context used for retrieving state.
   */
  public abstract class StepContext implements WindowingInternals.KeyedState {
    private final String stepName;

    public StepContext(String stepName) {
      this.stepName = stepName;
    }

    public String getStepName() {
      return stepName;
    }

    public ExecutionContext getExecutionContext() {
      return ExecutionContext.this;
    }

    public void noteOutput(WindowedValue<?> output) {
      ExecutionContext.this.noteOutput(output);
    }

    public void noteSideOutput(TupleTag<?> tag, WindowedValue<?> output) {
      ExecutionContext.this.noteSideOutput(tag, output);
    }

    /**
     * Stores the provided value in per-{@link com.google.cloud.dataflow.sdk.transforms.DoFn},
     * per-key state.  This state is in the form of a map from tags to arbitrary
     * encodable values.
     *
     * @throws IOException if encoding the given value fails
     */
    public abstract <T> void store(CodedTupleTag<T> tag, T value, Instant timestamp)
        throws IOException;

    @Override
    public <T> void store(CodedTupleTag<T> tag, T value) throws IOException {
      store(tag, value, BoundedWindow.TIMESTAMP_MAX_VALUE);
    }

    /**
     * Loads the values from the per-{@link com.google.cloud.dataflow.sdk.transforms.DoFn},
     * per-key state corresponding to the given tags.
     *
     * @throws IOException if decoding any of the requested values fails
     */
    @Override
    public abstract CodedTupleTagMap lookup(Iterable<? extends CodedTupleTag<?>> tags)
        throws IOException;

    /**
     * Loads the value from the per-{@link com.google.cloud.dataflow.sdk.transforms.DoFn},
     * per-key state corresponding to the given tag.
     *
     * @throws IOException if decoding the value fails
     */
    @Override
    public <T> T lookup(CodedTupleTag<T> tag) throws IOException {
      return lookup(Arrays.asList(tag)).get(tag);
    }

    /**
     * Writes the provided value to the list of values in stored state corresponding to the
     * provided tag.
     *
     * @throws IOException if encoding the given value fails
     */
    public <T> void writeToTagList(CodedTupleTag<T> tag, T value) throws IOException {
      writeToTagList(tag, value, BoundedWindow.TIMESTAMP_MAX_VALUE);
    }

    public abstract <T> void writeToTagList(CodedTupleTag<T> tag, T value, Instant timestamp)
        throws IOException;

    /**
     * Deletes the list corresponding to the given tag.
     */
    public abstract <T> void deleteTagList(CodedTupleTag<T> tag);

    /**
     * Reads the elements of the list in stored state corresponding to the provided tag.
     *
     * @throws IOException if decoding any of the requested values fails
     */
    public <T> Iterable<T> readTagList(CodedTupleTag<T> tag) throws IOException {
      return readTagLists(Arrays.asList(tag)).get(tag);
    }

    /**
     * Reads the elements of the list in stored state corresponding to the provided tag.
     *
     * @throws IOException if decoding any of the requested values fails
     */
    public abstract <T> Map<CodedTupleTag<T>, Iterable<T>> readTagLists(
        Iterable<CodedTupleTag<T>> tags) throws IOException;
  }
}
