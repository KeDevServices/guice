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
import javax.transaction.TransactionRequiredException;
import javax.transaction.Transactional;
import javax.transaction.TransactionalException;
import java.util.Date;

import static javax.transaction.Transactional.TxType.MANDATORY;

/**
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
public class MandatoryBehaviorTest extends TestCase {

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
   * If called outside a transaction context, a TransactionalException with a
   * nested TransactionRequiredException must be thrown.
   */
  public void testIfTransactionalExceptionOutsideTransactionContext() throws Exception {
    assertTrue(!injector.getInstance(EntityManager.class).getTransaction().isActive());

    try {
      injector
          .getInstance(MandatoryBehaviorTest.TransactionalObject.class)
          .runOperationInTxn1();
    } catch (Exception e) {
      assertTrue(e instanceof TransactionalException);
      assertTrue(e.getCause() instanceof TransactionRequiredException);
    }

    injector.getInstance(UnitOfWork.class).end();
  }

  /**
   * Test to ensure:
   * If called inside a transaction context, managed bean method execution will
   * then continue under that context.
   */
  public void testJoiningOfTransaction() throws Exception {
    Provider<EntityManager> em = injector.getProvider(EntityManager.class);

    injector
        .getInstance(MandatoryBehaviorTest.TransactionalObject.class)
        .runOperationInTxn2();

    injector.getInstance(UnitOfWork.class).end();

    //test that the data has been stored
    Object result1 = em.get().createQuery("from JpaTestEntity where text = :text")
        .setParameter("text", UNIQUE_TEXT_1).getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue("odd result returned fatal", result1 instanceof JpaTestEntity);
    assertEquals("queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT_1, ((JpaTestEntity) result1).getText());

    Object result2 = em.get().createQuery("from JpaTestEntity2 where text = :text")
        .setParameter("text", UNIQUE_TEXT_2).getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue("odd result returned fatal", result2 instanceof JpaTestEntity2);
    assertEquals("queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT_2, ((JpaTestEntity2) result2).getText());
  }

  public static class TransactionalObject {
    @Inject
    Provider<EntityManager> em;

    @Transactional(MANDATORY)
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
    }

    @Transactional(MANDATORY)
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
