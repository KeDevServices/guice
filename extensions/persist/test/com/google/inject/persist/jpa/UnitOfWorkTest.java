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
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import junit.framework.TestCase;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.util.Date;

/**
 * @author Joachim Klein (jk@kedev.eu, luno1977@gmail.com)
 */
public class UnitOfWorkTest extends TestCase {

  private Injector injector;

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
   * Ensure that suspend() throws IllegalStateException if UnitOfWork is not started
   */
  public void testCallingSuspendWithoutStartingUnitOfWork() throws Exception {
    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);

    try {
      unitOfWork.suspend();
    } catch (IllegalStateException ise) {
      assertTrue(ise.getMessage().contains("Work never begun"));
    }
  }

  /**
   * Ensure that resume() throws IllegalStateException if UnitOfWork is not started
   */
  public void testCallingResumeWithoutStartingUnitOfWork() throws Exception {
    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);

    try {
      unitOfWork.resume();
    } catch (IllegalStateException ise) {
      assertTrue(ise.getMessage().contains("Work never begun"));
    }
  }

  /**
   * Ensure that end() throws IllegalStateException suspend() was called without closing resume() call
   */
  public void testCallingEndIfClosingResumeIsMissing() throws Exception {
    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);

    try {
      unitOfWork.begin();
      unitOfWork.suspend();
      unitOfWork.end();
    } catch (IllegalStateException ise) {
      assertTrue(ise.getMessage().contains("called UnitOfWork.suspend() 1 time(s) without"));
      assertTrue(ise.getMessage().contains("closing call to UnitOfWork.UnitOfWork.resume()"));
    }
  }

  /**
   * Ensure that resume() throws IllegalStateException if no initial suspend() call is available
   */
  public void testCallingResumeWithoutInitialSuspend() throws Exception {
    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);

    try {
      unitOfWork.begin();
      unitOfWork.resume();
    } catch (IllegalStateException ise) {
      assertTrue(ise.getMessage().contains("Unbalanced call to UnitOfWork.resume()"));
    }
  }

  /**
   * Ensure that suspend() opens an new EntityManager and
   * resume() closes the current EntityManager and the previous one is resumed.
   */
  public void testSuspendProvidesNewEntityManager() throws Exception {
    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);

    Provider<EntityManager> emProvider = injector.getProvider(EntityManager.class);

    unitOfWork.begin();
    EntityManager em1 = emProvider.get();

    unitOfWork.suspend();
    EntityManager em2 = emProvider.get();

    unitOfWork.resume();
    EntityManager em3 = emProvider.get();

    assertTrue(em1 != em2);
    assertTrue(em1 == em3);
  }
}
