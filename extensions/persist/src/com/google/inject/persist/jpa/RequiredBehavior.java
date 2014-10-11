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
 * from TxType.REQUIRED:<br>
 * <p>If called outside a transaction context, the interceptor must begin a new
 * JTA transaction, the managed bean method execution must then continue
 * inside this transaction context, and the transaction must be completed by
 * the interceptor.</p>
 * <p>If called inside a transaction context, the managed bean
 * method execution must then continue inside this transaction context.</p>
 *
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
final class RequiredBehavior extends TransactionalBehavior {

  protected RequiredBehavior(TransactionalMetadata transactionalMetadata) {
    super(transactionalMetadata);
  }

  @Override
  Object handleInvocation(MethodInvocation methodInvocation,
                          Provider<EntityManager> emProvider,
                          UnitOfWork unitOfWork) throws Throwable {

    boolean didIStartWorkUnit = false;

    // Should we start a unit of work?
    if (!unitOfWork.isWorking()) {
      unitOfWork.begin();
      didIStartWorkUnit = true;
    }

    EntityManager em = emProvider.get();

    // Allow 'joining' of transactions if there is an enclosing @Transactional method.
    if (em.getTransaction().isActive()) {
      return methodInvocation.proceed();
    }

    final EntityTransaction txn = em.getTransaction();
    txn.begin();

    Object result;
    try {
      result = methodInvocation.proceed();
    } catch (Exception e) {
      //commit transaction only if rollback didn't occur
      if (rollbackIfNecessary(e, txn)) {
        txn.commit();
      }

      //propagate whatever exception is thrown anyway
      throw e;
    } finally {
      // Close the em if necessary (guarded so this code doesn't run unless catch fired).
      if (didIStartWorkUnit && !txn.isActive()) {
        unitOfWork.end();
      }
    }

    //everything was normal so commit the txn (do not move into try block above as it
    //  interferes with the advised method's throwing semantics)
    try {
      txn.commit();
    } finally {
      //close the em if necessary
      if (didIStartWorkUnit) {
        unitOfWork.end();
      }
    }

    //or return result
    return result;
  }
}
