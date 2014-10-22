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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Stack;

import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
@Singleton
class JpaPersistService implements Provider<EntityManager>, UnitOfWork, PersistService {
  private final ThreadLocal<Stack<EntityManager>> entityManager = new ThreadLocal<Stack<EntityManager>>();

  private final String persistenceUnitName;
  private final Map<?,?> persistenceProperties;

  @Inject
  public JpaPersistService(@Jpa String persistenceUnitName,
      @Nullable @Jpa Map<?,?> persistenceProperties) {
    this.persistenceUnitName = persistenceUnitName;
    this.persistenceProperties = persistenceProperties;
  }

  public EntityManager get() {
    if (!isWorking()) {
      begin();
    }

    EntityManager em = entityManager.get().peek();
    Preconditions.checkState(null != em, "Requested EntityManager outside work unit. "
        + "Try calling UnitOfWork.begin() first, or use a PersistFilter if you "
        + "are inside a servlet environment.");

    return em;
  }

  public boolean isWorking() {
    return entityManager.get() != null;
  }

  /**
   * Suspends the current EntityManager and creates a new one on top of the stack
   */
  public void suspend() {
    Preconditions.checkState(isWorking(), "Work never begun.");

    Stack<EntityManager> stack = entityManager.get();
    stack.push(emFactory.createEntityManager());
    entityManager.set(stack);
  }

  /**
   * Stops the current EntityManager and removes them from the stack
   * the underlying EntityManager is resumed.
   */
  public void resume() {
    Preconditions.checkState(isWorking(), "Work never begun.");

    Stack<EntityManager> stack = entityManager.get();

    if (stack.size() > 1) {
      //There is at least one manager previously suspended
      EntityManager current = stack.pop();
      entityManager.set(stack);
      current.close();
    } else {
      //Just one (or even no) EntityManager left, so there is no one to resume
      throw new IllegalStateException("Unbalanced call to UnitOfWork.resume(). Did you forget to UnitOfWork.suspend() before?");
    }

  }

  public void begin() {
    Preconditions.checkState(!isWorking(),
        "Work already begun on this thread. Looks like you have called UnitOfWork.begin() twice"
         + " without a balancing call to end() in between.");

    Stack<EntityManager> stack = new Stack<EntityManager>();
    stack.push(emFactory.createEntityManager());
    entityManager.set(stack);
  }

  public void end() {
    // Let's not penalize users for calling end() multiple times.
    if (!isWorking()) {
      return;
    }

    Stack<EntityManager> ems = entityManager.get();

    try {
      //first ensure to close them all
      for (EntityManager em : ems) {
        em.close();
      }
    } finally {
      entityManager.remove();
    }

    //check if there was more than one EntityManager to close
    if (ems.size() > 1) {
      throw new IllegalStateException("You called UnitOfWork.suspend() " + (ems.size()-1) +
          " time(s) without having a corresponding closing call to UnitOfWork.UnitOfWork.resume().");
    }
  }

  private volatile EntityManagerFactory emFactory;

  @VisibleForTesting
  synchronized void start(EntityManagerFactory emFactory) {
    this.emFactory = emFactory;
  }

  public synchronized void start() {
    Preconditions.checkState(null == emFactory, "Persistence service was already initialized.");

    if (null != persistenceProperties) {
      this.emFactory = Persistence
          .createEntityManagerFactory(persistenceUnitName, persistenceProperties);
    } else {
      this.emFactory = Persistence.createEntityManagerFactory(persistenceUnitName);
    }
  }

  public synchronized void stop() {
    Preconditions.checkState(emFactory.isOpen(), "Persistence service was already shut down.");
    emFactory.close();
  }

  @Singleton
  public static class EntityManagerFactoryProvider implements Provider<EntityManagerFactory> {
    private final JpaPersistService emProvider;

    @Inject
    public EntityManagerFactoryProvider(JpaPersistService emProvider) {
      this.emProvider = emProvider;
    }

    public EntityManagerFactory get() {
      assert null != emProvider.emFactory;
      return emProvider.emFactory;
    }
  }
  
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  private @interface Nullable { }

}
