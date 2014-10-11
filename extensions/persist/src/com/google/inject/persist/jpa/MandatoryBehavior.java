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
import javax.transaction.TransactionRequiredException;
import javax.transaction.TransactionalException;

/**
 * from TxType.MANDATORY:<br>
 * <p>If called outside a transaction context, a TransactionalException with a
 * nested TransactionRequiredException must be thrown.</p>
 * <p>If called inside a transaction context, managed bean method execution will
 * then continue under that context.</p>
 *
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
final class MandatoryBehavior extends TransactionalBehavior {

  protected MandatoryBehavior(TransactionalMetadata transactionalMetadata) {
    super(transactionalMetadata);
  }

  @Override
  Object handleInvocation(MethodInvocation methodInvocation,
                          Provider<EntityManager> emProvider,
                          UnitOfWork unitOfWork) throws Throwable {

    // We have to 'join' an active transaction started by an enclosing @Transactional method.
    if (unitOfWork.isWorking() && emProvider.get().getTransaction().isActive()) {
      return methodInvocation.proceed();
    } else {
      throw new TransactionalException(
          "No active transaction in transactional method declared mandatory " +
          "(cf. javax.transaction.Transactional.TxType.MANDATORY)",
          new TransactionRequiredException());
    }
  }
}
