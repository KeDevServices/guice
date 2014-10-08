/**
 * Copyright (C) 2010 Google, Inc.
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
 */
package com.google.inject.persist.jpa;

import javax.transaction.Transactional;

/**
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
class TransactionalMetadata {

  private Transactional.TxType txType;
  private Class[] rollbackOn = {};
  private Class[] dontRollbackOn = {};

  private TransactionalMetadata() {
    txType = Transactional.TxType.REQUIRED;
  }

  public static TransactionalMetadata defaultMetadata() {
    return new TransactionalMetadata();
  }

  public static TransactionalMetadata fromJtaTranscational(
      javax.transaction.Transactional jtaTransactional) {
    TransactionalMetadata tb = new TransactionalMetadata();
    tb.txType = jtaTransactional.value();
    tb.rollbackOn = jtaTransactional.rollbackOn();
    tb.dontRollbackOn = jtaTransactional.dontRollbackOn();
    return tb;
  }

  public static TransactionalMetadata fromGuiceTranscational(
      com.google.inject.persist.Transactional guiceTransactional) {
    TransactionalMetadata tb = new TransactionalMetadata();
    tb.rollbackOn = guiceTransactional.rollbackOn();
    tb.dontRollbackOn = guiceTransactional.ignore();
    return tb;
  }

  public Transactional.TxType getTxType() {
    return txType;
  }

  public Class[] getRollbackOn() {
    return rollbackOn;
  }

  public Class[] getDontRollbackOn() {
    return dontRollbackOn;
  }
}
