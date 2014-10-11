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

  private TransactionalMetadata transactionalMetadata;

  protected TransactionalBehavior(TransactionalMetadata transactionalMetadata) {
    this.transactionalMetadata = transactionalMetadata;
  }

  public static TransactionalBehavior from(TransactionalMetadata transactionalMetadata) {
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
   * Returns True if rollback DID NOT HAPPEN (i.e. if commit should continue).
   *
   * @param e The exception to test for rollback
   * @param txn A JPA Transaction to issue rollbacks on
   */
  protected boolean rollbackIfNecessary(Exception e, EntityTransaction txn) {
    boolean commit = true;

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
        commit = false;

        //check ignore clauses (supercedes rollback clause)
        for (Class<? extends Exception> exceptOn : transactionalMetadata.getDontRollbackOn()) {
          //An exception to the rollback clause was found, DON'T rollback
          // (i.e. commit and throw anyway)
          if (exceptOn.isInstance(e)) {
            commit = true;
            break;
          }
        }

        //rollback only if nothing matched the ignore check
        if (!commit) {
          txn.rollback();
        }

        //otherwise continue to commit
        break;
      }
    }

    return commit;
  }

  abstract Object handleInvocation(MethodInvocation methodInvocation,
                                   Provider<EntityManager> emProvider,
                                   UnitOfWork unitOfWork) throws Throwable;
}