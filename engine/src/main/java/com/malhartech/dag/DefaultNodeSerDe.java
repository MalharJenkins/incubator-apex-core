/**
 * Copyright (c) 2012-2012 Malhar, Inc.
 * All rights reserved.
 */
package com.malhartech.dag;

import java.io.InputStream;
import java.io.OutputStream;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Default serializer that uses Kryo.
 */
public class DefaultNodeSerDe implements NodeSerDe {

  @Override
  public Object read(InputStream is)
  {
    Kryo kryo = new Kryo();
    kryo.setClassLoader(Thread.currentThread().getContextClassLoader());
    return kryo.readClassAndObject(new Input(is));
  }

  @Override
  public void write(Object o, OutputStream os)
  {
    Kryo kryo = new Kryo();
    Output output = new Output(os);
    kryo.writeClassAndObject(output, o);
    output.flush();
  }

}