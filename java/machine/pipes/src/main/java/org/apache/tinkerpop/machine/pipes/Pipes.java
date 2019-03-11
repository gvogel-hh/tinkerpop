/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.machine.pipes;

import org.apache.tinkerpop.machine.bytecode.Bytecode;
import org.apache.tinkerpop.machine.bytecode.BytecodeUtil;
import org.apache.tinkerpop.machine.functions.CFunction;
import org.apache.tinkerpop.machine.functions.FilterFunction;
import org.apache.tinkerpop.machine.functions.InitialFunction;
import org.apache.tinkerpop.machine.functions.MapFunction;
import org.apache.tinkerpop.machine.functions.NestedFunction;
import org.apache.tinkerpop.machine.functions.ReduceFunction;
import org.apache.tinkerpop.machine.pipes.util.BasicReducer;
import org.apache.tinkerpop.machine.processor.Processor;
import org.apache.tinkerpop.machine.traversers.Traverser;
import org.apache.tinkerpop.machine.traversers.TraverserFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Pipes<C, S, E> implements Processor<C, S, E> {

    private final List<Step<?, ?, ?>> steps = new ArrayList<>();
    private Step<C, ?, E> endStep;
    private Step<C, S, ?> startStep = EmptyStep.instance();

    public Pipes(final List<CFunction<C>> functions, final TraverserFactory<C> traverserFactory) {
        AbstractStep<C, ?, ?> previousStep = EmptyStep.instance();
        for (final CFunction<?> function : functions) {
            if (function instanceof NestedFunction)
                ((NestedFunction<C, ?, ?>) function).setProcessor(new Pipes(((NestedFunction<C, S, E>) function).getFunctions(), traverserFactory));
            final AbstractStep nextStep;
            if (function instanceof FilterFunction)
                nextStep = new FilterStep(previousStep, (FilterFunction<C, ?>) function);
            else if (function instanceof MapFunction)
                nextStep = new MapStep(previousStep, (MapFunction<C, ?, ?>) function);
            else if (function instanceof InitialFunction)
                // TODO: traverser factory
                nextStep = new InitialStep((InitialFunction<C, S>) function, traverserFactory);
            else if (function instanceof ReduceFunction)
                nextStep = new ReduceStep(previousStep, (ReduceFunction<C, ?, ?>) function, new BasicReducer<>(((ReduceFunction<C, ?, ?>) function).getInitialValue()), traverserFactory);
            else
                throw new RuntimeException("You need a new step type:" + function);

            if (EmptyStep.instance() == this.startStep)
                this.startStep = nextStep;

            this.steps.add(nextStep);
            previousStep = nextStep;
        }
        this.endStep = (Step<C, ?, E>) previousStep;
    }

    public Pipes(final Bytecode<C> bytecode) {
        this(BytecodeUtil.compile(BytecodeUtil.strategize(bytecode)), BytecodeUtil.getTraverserFactory(bytecode).get());
    }

    @Override
    public void addStart(final Traverser<C, S> traverser) {
        this.startStep.addStart(traverser);
    }

    @Override
    public Traverser<C, E> next() {
        return this.endStep.next();
    }

    @Override
    public boolean hasNext() {
        return this.endStep.hasNext();
    }

    @Override
    public void reset() {
        for (final Step<?, ?, ?> step : this.steps) {
            step.reset();
        }
    }

    @Override
    public String toString() {
        return this.steps.toString();
    }
}
