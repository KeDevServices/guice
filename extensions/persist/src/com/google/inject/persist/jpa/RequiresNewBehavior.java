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

import com.google.inject.Provider;
import com.google.inject.persist.UnitOfWork;
import org.aopalliance.intercept.MethodInvocation;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

/**
 *
 * from TxType.REQUIRES_NEW:<br>
 * <p>If called outside a transaction context, the interceptor must begin a new
 * JTA transaction, the managed bean method execution must then continue
 * inside this transaction context, and the transaction must be completed by
 * the interceptor.</p>
 * <p>If called inside a transaction context, the current transaction context must
 * be suspended, a new JTA transaction will begin, the managed bean method
 * execution must then continue inside this transaction context, the transaction
 * must be completed, and the previously suspended transaction must be resumed.</p>
 *
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
final class RequiresNewBehavior extends TransactionalBehavior {

  protected RequiresNewBehavior(TransactionalMetadata transactionalMetadata) {
    super(transactionalMetadata);
  }

  @Override
  Object handleInvocation(MethodInvocation methodInvocation,
                          Provider<EntityManager> emProvider,
                          UnitOfWork unitOfWork) throws Throwable {

    boolean didIStartWorkUnit = false;
    boolean didISuspendTransaction = false;

    // Should we start a unit of work?
    if (!unitOfWork.isWorking()) {
      unitOfWork.begin();
      didIStartWorkUnit = true;
    }

    //Suspend running transactions
    if (emProvider.get().getTransaction().isActive()) {
      unitOfWork.suspend(); // => New EntityManager
      didISuspendTransaction = true;
    }

    final EntityTransaction txn = emProvider.get().getTransaction();
    txn.begin();

    Object result;
    try {
      result = methodInvocation.proceed();
    } catch (Exception e) {
      //commit or rollback transaction
      rollbackOrCommit(e, txn);

      //propagate whatever exception is thrown anyway
      throw e;
    } finally {
      // (guarded so this code doesn't run unless catch fired).
      if (!txn.isActive()) {
        close(didISuspendTransaction, didIStartWorkUnit, unitOfWork);
      }
    }

    //everything was normal so commit the txn (do not move into try block above as it
    //  interferes with the advised method's throwing semantics)
    try {
      txn.commit();
    } finally {
      close(didISuspendTransaction, didIStartWorkUnit, unitOfWork);
    }

    //or return result
    return result;
  }

  private void close(
      boolean didISuspendTransaction,
      boolean didIStartWorkUnit,
      UnitOfWork unitOfWork) {
    // Resume
    if (didISuspendTransaction) {
      unitOfWork.resume();
    }

    // Close the em if necessary
    if (didIStartWorkUnit) {
      unitOfWork.end();
    }
  }
}
