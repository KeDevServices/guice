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

import com.google.common.collect.Lists;
import com.google.inject.Provider;
import com.google.inject.persist.UnitOfWork;
import org.aopalliance.intercept.MethodInvocation;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
abstract class TransactionalBehavior {

  private final TransactionalMetadata transactionalMetadata;

  protected TransactionalBehavior(final TransactionalMetadata transactionalMetadata) {
    this.transactionalMetadata = transactionalMetadata;
  }

  public static TransactionalBehavior from(final TransactionalMetadata transactionalMetadata) {
    switch (transactionalMetadata.getTxType()) {
      case REQUIRED:
        return new RequiredBehavior(transactionalMetadata);
      case REQUIRES_NEW:
        return new RequiresNewBehavior(transactionalMetadata);
      case MANDATORY:
        return new MandatoryBehavior(transactionalMetadata);
      case SUPPORTS:
        return new SupportsBehavior(transactionalMetadata);
      case NOT_SUPPORTED:
        return new NotSupportedBehavior(transactionalMetadata);
      case NEVER:
        return new NeverBehavior(transactionalMetadata);
    }

    throw new IllegalStateException("Unknown TxType not handled.");
  }


  /**
   * Decides if a transaction need to be rollback or can be commited
   * if a exception was thrown.
   *
   * @param e the exception that was thrown during transactional method execution
   * @param txn the current (active) transaction
   */
  protected void rollbackOrCommit(final Exception e, final EntityTransaction txn) {
    if (isRollbackNecessary(e)) {
      txn.rollback();
    } else {
      txn.commit();
    }
  }

  /**
   * @return true, if rollback need to be executed.
   * @param e The exception to test for rollback
   */
  private boolean isRollbackNecessary(final Exception e) {
    boolean rollback = false;

    List<Class<? extends Exception>> rollbackOnList =
        Lists.<Class<? extends Exception>>newArrayList(transactionalMetadata.getRollbackOn());
    /*
     Form JTA 1.2 Spec (jk@keDev.eu):
     By default checked exceptions do not result in the transactional interceptor
     marking the transaction for rollback and instances of RuntimeException and its
     subclasses do.
    */
    if (rollbackOnList.isEmpty()) {
      rollbackOnList.add(RuntimeException.class);
    }

    //check rollback clauses
    for (Class<? extends Exception> rollBackOn : rollbackOnList) {

      //if one matched, try to perform a rollback
      if (rollBackOn.isInstance(e)) {
        rollback = true;

        //check ignore clauses (supercedes rollback clause)
        for (Class<? extends Exception> exceptOn : transactionalMetadata.getDontRollbackOn()) {
          //An exception to the rollback clause was found, DON'T rollback
          // (i.e. commit and throw anyway)
          if (exceptOn.isInstance(e)) {
            rollback = false;
            break;
          }
        }

        //otherwise continue to commit
        break;
      }
    }

    return rollback;
  }

  abstract Object handleInvocation(MethodInvocation methodInvocation,
                                   Provider<EntityManager> emProvider,
                                   UnitOfWork unitOfWork) throws Throwable;
}