/*
 * Copyright 2010 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package org.pitest.extension.common;

import java.util.Collections;
import java.util.List;

import org.pitest.extension.InstantiationStrategy;
import org.pitest.extension.TestStep;
import org.pitest.teststeps.NameStringConstructorInstantiateStep;

public class NamedTestSingleStringConstructorInstantiationStrategy implements
    InstantiationStrategy {

  public boolean canInstantiate(final Class<?> clazz) {
    final Class<?>[] oneString = { String.class };
    try {
      clazz.getDeclaredConstructor(oneString);
      return true;
    } catch (final Throwable e) {
      return false;
    }
  }

  public List<TestStep> instantiations(final Class<?> clazz) {
    return Collections
        .<TestStep> singletonList(new NameStringConstructorInstantiateStep(
            clazz));
  }

}