/**
 * Copyright (c) 2012-2012 Malhar, Inc. All rights reserved.
 */
package com.malhartech.stream;

import com.malhartech.api.*;
import com.malhartech.engine.*;
import com.malhartech.util.AttributeMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test for message flow through DAG
 */
public class InlineStreamTest
{
  private static final Logger LOG = LoggerFactory.getLogger(InlineStreamTest.class);
  private Object prev;

  @Test
  @SuppressWarnings("SleepWhileInLoop")
  public void test() throws Exception
  {
    final int totalTupleCount = 5000;
    prev = null;

    final PassThroughNode operator1 = new PassThroughNode();
    final GenericNode node1 = new GenericNode("node1", operator1);
    operator1.setup(new OperatorContext(null, null, null));

    final PassThroughNode operator2 = new PassThroughNode();
    final GenericNode node2 = new GenericNode("node2", operator2);
    operator2.setup(new OperatorContext(null, null, null));

    StreamContext streamContext = new StreamContext("node1->node2");
    InlineStream stream = new InlineStream();
    stream.setup(streamContext);

    node1.connectOutputPort("output", stream);

    Sink s = node2.connectInputPort("input", stream);
    stream.setSink("node2.input", s);

    Sink<Object> sink = new Sink<Object>()
    {
      /**
       *
       * @param t the value of t
       */
      @Override
      public void process(Object payload)
      {
        if (payload instanceof Tuple) {
          // we ignore the control tuple
        }
        else {
          if (prev == null) {
            prev = payload;
          }
          else {
            if (Integer.valueOf(payload.toString()) - Integer.valueOf(prev.toString()) != 1) {
              synchronized (InlineStreamTest.this) {
                InlineStreamTest.this.notify();
              }
            }

            prev = payload;
          }

          if (Integer.valueOf(prev.toString()) == totalTupleCount - 1) {
            synchronized (InlineStreamTest.this) {
              InlineStreamTest.this.notify();
            }
          }
        }
      }
    };
    node2.connectOutputPort("output", sink);

    sink = (Sink<Object>)node1.connectInputPort("input", new Sink()
    {
      @Override
      public void process(Object tuple)
      {
        throw new UnsupportedOperationException("Not supported yet.");
      }
    });

    sink.process(StramTestSupport.generateBeginWindowTuple("irrelevant", 0));
    for (int i = 0; i < totalTupleCount; i++) {
      sink.process(i);
    }
    sink.process(StramTestSupport.generateEndWindowTuple("irrelevant", 0));

    stream.activate(streamContext);

    Map<String, Node> activeNodes = new ConcurrentHashMap<String, Node>();
    launchNodeThread(node1, activeNodes);
    launchNodeThread(node2, activeNodes);

    synchronized (this) {
      this.wait(100);
    }

    Assert.assertTrue("last tuple", prev != null && totalTupleCount - Integer.valueOf(prev.toString()) == 1);
    Assert.assertEquals("active operators", 2, activeNodes.size());

    for (Node node: activeNodes.values()) {
      node.deactivate();
    }
    stream.deactivate();

    for (int i = 0; i < 10; i++) {
      Thread.sleep(20);
      if (activeNodes.isEmpty()) {
        break;
      }
    }

    operator2.teardown();
    operator1.teardown();
    stream.teardown();

    Assert.assertEquals("active operators", 0, activeNodes.size());
  }
  final AtomicInteger counter = new AtomicInteger(0);

  private void launchNodeThread(final Node node, final Map<String, Node> activeNodes)
  {
    Runnable nodeRunnable = new Runnable()
    {
      @Override
      public void run()
      {
        String id = String.valueOf(counter.incrementAndGet());
        OperatorContext ctx = new OperatorContext(id, Thread.currentThread(), new AttributeMap.DefaultAttributeMap<Context.OperatorContext>());
        activeNodes.put(ctx.getId(), node);
        node.activate(ctx);
        activeNodes.remove(ctx.getId());
      }
    };

    Thread launchThread = new Thread(nodeRunnable);
    launchThread.start();
  }

  /**
   * Operator implementation that simply passes on any tuple received
   */
  public static class PassThroughNode<T> extends BaseOperator
  {
    public final DefaultInputPort<T> input = new DefaultInputPort<T>(this)
    {
      @Override
      public void process(T tuple)
      {
        output.emit(tuple);
      }
    };
    public final DefaultOutputPort<T> output = new DefaultOutputPort(this);
    private boolean logMessages = false;

    public boolean isLogMessages()
    {
      return logMessages;
    }

    public void setLogMessages(boolean logMessages)
    {
      this.logMessages = logMessages;
    }
  }
}
