/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2017 Andras Berkes [andras.berkes@programmer.net]<br>
 * Based on Moleculer Framework for NodeJS [https://moleculer.services].
 * <br><br>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:<br>
 * <br>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.<br>
 * <br>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package services.moleculer.web;

import org.junit.Test;

import com.openpojo.reflection.PojoClass;
import com.openpojo.reflection.PojoClassFilter;
import com.openpojo.validation.Validator;
import com.openpojo.validation.ValidatorBuilder;
import com.openpojo.validation.test.impl.GetterTester;
import com.openpojo.validation.test.impl.SetterTester;

import junit.framework.TestCase;

public class PojoTest extends TestCase {

	private Validator validator;
	private PojoClassFilter filterTestClasses = new FilterTestClasses();

	@Override
	protected void setUp() throws Exception {
		validator = ValidatorBuilder.create().with(new SetterTester()).with(new GetterTester()).build();
	}

	@Test
	public void testProductionClasses() throws Exception {
		try {
			validator.validate("services.moleculer.web.middleware", filterTestClasses);
			validator.validate("services.moleculer.web.template", filterTestClasses);
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	private static class FilterTestClasses implements PojoClassFilter {
		public boolean include(PojoClass pojoClass) {
			String n = pojoClass.getName();
			boolean enable = !n.contains("Test") && !n.contains("$") && !n.contains("TopLevelCache");
			if (enable) {
				// System.out.println(pojoClass.getName());
			}
			return enable;
		}
	}

}
