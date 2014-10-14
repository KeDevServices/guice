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
import javax.transaction.InvalidTransactionException;
import javax.transaction.TransactionRequiredException;
import javax.transaction.Transactional;
import javax.transaction.TransactionalException;
import java.util.Date;

import static javax.transaction.Transactional.TxType.NEVER;

/**
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
public class NeverBehaviorTest extends TestCase {

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
   * Test to ensure:
   * If called outside a transaction context, managed bean method execution
   * must then continue outside a transaction context.
   */
  public void testTransactionDoNotStartedOutsideTransactionContext() throws Exception {
    assertTrue(!injector.getInstance(EntityManager.class).getTransaction().isActive());

    injector
        .getInstance(NeverBehaviorTest.TransactionalObject.class)
        .runOperationInTxn1();

    injector.getInstance(UnitOfWork.class).end();
  }

  /**
   * Test to ensure:
   * If called inside a transaction context, a TransactionalException with
   * a nested InvalidTransactionException must be thrown.
   */
  public void testIfTransactionalExceptionInsideTransactionContext() throws Exception {
    assertTrue(!injector.getInstance(EntityManager.class).getTransaction().isActive());

    try {
      injector
          .getInstance(NeverBehaviorTest.TransactionalObject.class)
          .runOperationInTxn2();
    } catch (Exception e) {
      assertTrue(e instanceof TransactionalException);
      assertTrue(e.getCause() instanceof InvalidTransactionException);
    }

    injector.getInstance(UnitOfWork.class).end();
  }

  public static class TransactionalObject {
    @Inject
    Provider<EntityManager> em;

    @Transactional(NEVER)
    public void runOperationInTxn1() {
      assertTrue(!em.get().getTransaction().isActive());
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

      assertTrue(!txn.isActive());
    }

    @Transactional(NEVER)
    public void runNestedOperationTxn(
        final EntityManager parentManager,
        final EntityTransaction parentTxn,
        final JpaTestEntity parentEntity) {

      fail(); //Do not execute this!
    }
  }
}

