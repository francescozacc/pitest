/*
 * Copyright 2011 Henry Coles
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
package org.pitest.mutationtest;

import static org.junit.Assert.assertFalse;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.pitest.coverage.execute.CoverageOptions;
import org.pitest.util.Glob;

public class ReportOptionsTest {
  private ReportOptions testee;

  @Before
  public void setUp() {
    this.testee = new ReportOptions();
  }

  @Test
  public void shouldNotAllowUserToCalculateCoverageForCoreClasses() {
    this.testee.setTargetClasses(Glob.toGlobPredicates(Collections
        .singleton("*")));
    this.testee.setClassesInScope(Glob.toGlobPredicates(Collections
        .singleton("*")));
    final CoverageOptions actual = this.testee.createCoverageOptions();
    assertFalse(actual.getFilter().apply("java/Integer"));

  }

}
