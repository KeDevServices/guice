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

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import junit.framework.TestCase;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.transaction.Transactional;
import java.util.Date;

/**
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
public class RequiredBehaviorTest extends TestCase {

  private Injector injector;
  private static final String UNIQUE_TEXT_1 = "some unique text" + new Date();
  private static final String UNIQUE_TEXT_2 = "some other unique text" + new Date();

  @Override
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));

    //startup persistence
    injector.getInstance(PersistService.class).start();
  }

  @Override
  public void tearDown() {
    injector.getInstance(EntityManagerFactory.class).close();
  }

  /**
   * Test if new tx is created if no transaction is active
   */
  public void testStartOfTransaction() throws Exception {
    assertTrue(!injector.getInstance(EntityManager.class).getTransaction().isActive());

    injector
        .getInstance(RequiredBehaviorTest.TransactionalObject.class)
        .runOperationInTxn1();

    injector.getInstance(UnitOfWork.class).end();
  }

  /**
   * Test if already active transaction is joined
   */
  public void testJoiningOfTransaction() throws Exception {
    injector
        .getInstance(RequiredBehaviorTest.TransactionalObject.class)
        .runOperationInTxn2();

    injector.getInstance(UnitOfWork.class).end();
  }

  public static class TransactionalObject {
    @Inject
    Provider<EntityManager> em;

    @Transactional
    public void runOperationInTxn1() {
      assertTrue(em.get().getTransaction().isActive());
    }

    @Transactional
    public void runOperationInTxn2() {
      EntityManager manager = em.get();
      EntityTransaction txn = manager.getTransaction();

      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_1);
      em.get().persist(entity);

      assertTrue(manager.contains(entity));
      assertTrue(txn.isActive());

      runNestedOperationTxn(manager, txn, entity);
    }

    @Transactional
    public void runNestedOperationTxn(
        final EntityManager parentManager,
        final EntityTransaction parentTxn,
        final JpaTestEntity parentEntity) {

      EntityManager manager = em.get();
      EntityTransaction txn = manager.getTransaction();

      JpaTestEntity2 entity = new JpaTestEntity2();
      entity.setText(UNIQUE_TEXT_2);
      em.get().persist(entity);

      assertTrue(manager == parentManager);
      assertTrue(txn == parentTxn);

      assertTrue(txn.isActive()); //txn == parentTxn still active

      assertTrue(manager.contains(entity));
      assertTrue(manager.contains(parentEntity));
    }
  }
}