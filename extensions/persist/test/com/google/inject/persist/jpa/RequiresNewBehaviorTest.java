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
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import java.util.Date;

import static javax.transaction.Transactional.TxType.REQUIRES_NEW;

/**
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
public class RequiresNewBehaviorTest extends TestCase {


  //Test if new tx is created if no transaction is active
  //in this case the stack of EntityManager in UnitOfWork need to have size < 2

  //Test if new tx is created and old tx is suspended if a transaction is active
  //in this case the stack of EntityManager in UnitOfWork need to have size > 1

  //Test if rollback behavior of trasactional method is used
  //  if no transaction was active
  //  if transaction was active


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

  public void testIndependenceOfTransactions() {
    Provider<EntityManager> em = injector.getProvider(EntityManager.class);

    try {
      injector
          .getInstance(RequiresNewBehaviorTest.TransactionalObject.class)
          .runTxn2();
    } catch (Exception ise) {
      System.out.println("Yepp that thing is kept!");
      //Thats ok here!
    }
    injector.getInstance(UnitOfWork.class).end();

    //test that the data has been stored
    Object result = em.get().createQuery("from JpaTestEntity where text = :text")
        .setParameter("text", UNIQUE_TEXT_2).getSingleResult();
    injector.getInstance(UnitOfWork.class).end();

    assertTrue("odd result returned fatal", result instanceof JpaTestEntity);
    assertEquals("queried entity did not match--did automatic txn fail?",
        UNIQUE_TEXT_2, ((JpaTestEntity) result).getText());

    NoResultException noResult = null;
    try {
      Object result2 = em.get().createQuery("from JpaTestEntity2 where text = :text")
          .setParameter("text", "Some text for test entity 2").getSingleResult();

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

    @Transactional(value = REQUIRES_NEW, rollbackOn = Exception.class)
    public JpaTestEntity2 runTxnInside() throws Exception {
      JpaTestEntity2 entity = new JpaTestEntity2();
      entity.setText("Some text for test entity 2");
      em.get().persist(entity);

      throw new Exception("You can not ... no!");
    }

    @Transactional(REQUIRES_NEW)
    public void runTxn2() throws Exception {
      JpaTestEntity entity = new JpaTestEntity();
      entity.setText(UNIQUE_TEXT_2);
      em.get().persist(entity);

      runTxnInside();
    }
  }
}