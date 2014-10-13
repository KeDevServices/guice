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
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import java.util.Date;

import static javax.transaction.Transactional.TxType.REQUIRES_NEW;

/**
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
public class RequiresNewBehaviorTest extends TestCase {

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
        .getInstance(RequiresNewBehaviorTest.TransactionalObject.class)
        .runOperationInTxn2();

    injector.getInstance(UnitOfWork.class).end();
  }

  /**
   * Test if new tx is created and old tx is suspended if a transaction is active
   */
  public void testStartOfNewTransaction() throws Exception {
    injector
        .getInstance(RequiresNewBehaviorTest.TransactionalObject.class)
        .runOperationInTxn3();

    injector.getInstance(UnitOfWork.class).end();
  }

  /**
   * When nested transaction fails (throws an exception) the parent transaction
   * need to be commited.
   */
  public void testIndependenceOfTransactions() {
    Provider<EntityManager> em = injector.getProvider(EntityManager.class);

    try {
      injector
          .getInstance(RequiresNewBehaviorTest.TransactionalObject.class)
          .runOperationInTxn1();
    } catch (Exception ise) {
      System.out.println("Yepp that thing is kept!");
      //Thats ok here!
    }
    injector.getInstance(UnitOfWork.class).end();

    //test that the data has been stored
    Object result = em.get().createQuery("from JpaTestEntity where text = :text")
        .setParameter("text", UNIQUE_TEXT_1).getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue("odd result returned fatal", result instanceof JpaTestEntity);
    assertEquals("queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT_1, ((JpaTestEntity) result).getText());

    NoResultException noResult = null;
    try {
      Object result2 = em.get().createQuery("from JpaTestEntity2 where text = :text")
          .setParameter("text", UNIQUE_TEXT_2).getSingleResult();

    } catch (NoResultException nre) {
      noResult = nre;
    } finally {
      injector.getInstance(UnitOfWork.class).end();
    }

    assertNotNull(noResult);
  }

  public static class TransactionalObject {
    @Inject
    Provider<EntityManager> em;

    @Transactional(REQUIRES_NEW)
    public void runOperationInTxn1() throws Exception {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_1);
      em.get().persist(entity);

      runNestedOperationTxnThatFails();
    }

    @Transactional(value = REQUIRES_NEW, rollbackOn = Exception.class)
    public void runNestedOperationTxnThatFails() throws Exception {
      JpaTestEntity2 entity = new JpaTestEntity2();
      entity.setText(UNIQUE_TEXT_2);
      em.get().persist(entity);

      throw new Exception("You can not ... no!");
    }

    @Transactional(REQUIRES_NEW)
    public void runOperationInTxn2() {
      assertTrue(em.get().getTransaction().isActive());
    }

    @Transactional(REQUIRES_NEW)
    public void runOperationInTxn3() {
      EntityManager manager = em.get();
      EntityTransaction txn = manager.getTransaction();

      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_1);
      em.get().persist(entity);

      assertTrue(manager.contains(entity));
      assertTrue(txn.isActive());

      runNestedOperationTxn(manager, txn, entity);
    }

    @Transactional(REQUIRES_NEW)
    public void runNestedOperationTxn(
        final EntityManager parentManager,
        final EntityTransaction parentTxn,
        final JpaTestEntity parentEntity) {

      EntityManager manager = em.get();
      EntityTransaction txn = manager.getTransaction();

      JpaTestEntity2 entity = new JpaTestEntity2();
      entity.setText(UNIQUE_TEXT_2);
      em.get().persist(entity);

      assertTrue(manager != parentManager);
      assertTrue(txn != parentTxn);

      assertTrue(txn.isActive());
      assertTrue(parentTxn.isActive()); //still active!

      assertTrue(manager.contains(entity));
      assertTrue(!manager.contains(parentEntity));
      assertTrue(!parentManager.contains(entity));
    }
  }
}