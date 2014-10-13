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

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import javax.persistence.EntityManager;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
class JpaLocalTxnInterceptor implements MethodInterceptor {

  @Inject
  private final Provider<EntityManager> emProvider = null;

  @Inject
  private final UnitOfWork unitOfWork = null;

  public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    /*
      Possible performance improvements:
      a) cache TransactionalMetadata instances per method
      b) Refactor field transactionalMetadata in TransactionalBehavior as ThreadLocal to
         allow static instances of sub behaviors (RequiredBehavior, ...)

      Decision: Unnecessary complicated at the moment (Joachim Klein, jk@kedev.eu)
     */

    TransactionalMetadata transactionalMetadata = readTransactionalMetadata(methodInvocation);
    TransactionalBehavior transactionalBehavior = TransactionalBehavior.from(transactionalMetadata);
    return transactionalBehavior.handleInvocation(methodInvocation, emProvider, unitOfWork);
  }

  /**
   * Reads the TransactionalMetadata given by {@link com.google.inject.persist.Transactional} or
   * {@link javax.transaction.Transactional}.
   *
   * Method level takes precedence over class level.
   * A {@link javax.transaction.Transactional} annotation takes precedence over
   * {@link com.google.inject.persist.Transactional} annotation.
   *
   * @param methodInvocation
   * @return
   */
  private TransactionalMetadata readTransactionalMetadata(MethodInvocation methodInvocation) {
    TransactionalMetadata metadata = TransactionalMetadata.defaultMetadata();
    Method method = methodInvocation.getMethod();
    Class<?> targetClass = methodInvocation.getThis().getClass();

    Optional<TransactionalMetadata> methodData = readTransactionalFrom(method);
    if (methodData.isPresent()) {
      metadata = methodData.get();
    } else {
      Optional<TransactionalMetadata> classData = readTransactionalFrom(targetClass);
      if (classData.isPresent()) {
        metadata = classData.get();
      }
    }

    return metadata;
  }

  private Optional<TransactionalMetadata> readTransactionalFrom(AnnotatedElement annotatedElement) {
    Optional<javax.transaction.Transactional> jtaTransactional =
        Optional.fromNullable(annotatedElement.getAnnotation(javax.transaction.Transactional.class));
    Optional<Transactional> guiceTransactional =
        Optional.fromNullable(annotatedElement.getAnnotation(Transactional.class));

    if (jtaTransactional.isPresent()) {
      return Optional.of(
          TransactionalMetadata.fromJtaTranscational(jtaTransactional.get()));
    }

    if (guiceTransactional.isPresent()) {
      return Optional.of(
          TransactionalMetadata.fromGuiceTranscational(guiceTransactional.get()));
    }

    return Optional.absent();
  }
}
