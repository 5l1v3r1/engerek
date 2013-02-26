/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 * Portions Copyrighted 2011 Peter Prochazka
 */

package com.evolveum.midpoint.common.test;

import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import org.testng.AssertJUnit;
import static org.testng.AssertJUnit.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import com.evolveum.midpoint.common.policy.ValuePolicyGenerator;
import com.evolveum.midpoint.common.policy.PasswordPolicyUtils;
import com.evolveum.midpoint.common.policy.StringPolicyUtils;

import com.evolveum.midpoint.prism.util.PrismTestUtil;
import com.evolveum.midpoint.schema.MidPointPrismContextFactory;
import com.evolveum.midpoint.schema.constants.MidPointConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.result.OperationResultStatus;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.JAXBUtil;
import com.evolveum.midpoint.util.PrettyPrinter;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ProtectedStringType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.StringLimitType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.StringPolicyType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.UserType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ValuePolicyType;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class PasswordPolicyValidatorTest {

	public PasswordPolicyValidatorTest() {

	}

	public static final String BASE_PATH = "src/test/resources/policy/";

	private static final transient Trace LOGGER = TraceManager.getTrace(PasswordPolicyValidatorTest.class);

	@BeforeSuite
	public void setup() throws SchemaException, SAXException, IOException {
		PrettyPrinter.setDefaultNamespacePrefix(MidPointConstants.NS_MIDPOINT_PUBLIC_PREFIX);
		PrismTestUtil.resetPrismContext(MidPointPrismContextFactory.FACTORY);
	}
	
	@Test
	public void stringPolicyUtilsMinimalTest() throws JAXBException, SchemaException, FileNotFoundException {
		String filename = "password-policy-minimal.xml";
		String pathname = BASE_PATH + filename;
		File file = new File(pathname);
		ValuePolicyType pp = PrismTestUtil.unmarshalObject(file, ValuePolicyType.class);
		StringPolicyType sp = pp.getStringPolicy();
		StringPolicyUtils.normalize(sp);
		AssertJUnit.assertNotNull(sp.getCharacterClass());
		AssertJUnit.assertNotNull(sp.getLimitations().getLimit());
		AssertJUnit.assertTrue(-1 == sp.getLimitations().getMaxLength());
		AssertJUnit.assertTrue(0 == sp.getLimitations().getMinLength());
		AssertJUnit.assertTrue(0 == " !\"#$%&'()*+,-.01234567890:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
				.compareTo(sp.getCharacterClass().getValue()));
	}

	@Test
	public void stringPolicyUtilsComplexTest() {
		String filename = "password-policy-complex.xml";
		String pathname = BASE_PATH + filename;
		File file = new File(pathname);
		JAXBElement<ValuePolicyType> jbe = null;
		try {
			jbe = PrismTestUtil.unmarshalElement(file, ValuePolicyType.class);
		} catch (Exception e) {
			e.printStackTrace();
		}

		ValuePolicyType pp = jbe.getValue();
		StringPolicyType sp = pp.getStringPolicy();
		StringPolicyUtils.normalize(sp);
	}
	
	@Test
	public void testPasswordGeneratorComplexNegative() throws Exception {
		String filename = "password-policy-complex.xml";
		String pathname = BASE_PATH + filename;
		File file = new File(pathname);
		ValuePolicyType pp = PrismTestUtil.unmarshalObject(file, ValuePolicyType.class);
		OperationResult op = new OperationResult("passwordGeneratorComplexTest");
		
		op = new OperationResult("passwordGeneratorComplexTest");
		// Make switch some cosistency
		pp.getStringPolicy().getLimitations().setMinLength(2);
		pp.getStringPolicy().getLimitations().setMinUniqueChars(5);
		String psswd = ValuePolicyGenerator.generate(pp.getStringPolicy(), 10, op);
		op.computeStatus();
		assertNotNull(psswd);
		AssertJUnit.assertTrue(op.isAcceptable());

		// Switch to all must be first :-) to test if there is error
		for (StringLimitType l : pp.getStringPolicy().getLimitations().getLimit()) {
			l.setMustBeFirst(true);
		}
		LOGGER.info("Negative testing: passwordGeneratorComplexTest");
		psswd = ValuePolicyGenerator.generate(pp.getStringPolicy(), 10, op);
		assertNull(psswd);
		op.computeStatus();
		AssertJUnit.assertTrue(op.getStatus() == OperationResultStatus.FATAL_ERROR);
	}
	
	@Test
	public void testPasswordGeneratorComplex() throws Exception {
		passwordGeneratorTest("testPasswordGeneratorComplex", "password-policy-complex.xml");
	}
	
	@Test
	public void testPasswordGeneratorLong() throws Exception {
		passwordGeneratorTest("testPasswordGeneratorLong", "password-policy-long.xml");
	}

	@Test
	public void testPasswordGeneratorNumeric() throws Exception {
		passwordGeneratorTest("testPasswordGeneratorNumeric", "password-policy-numeric.xml");
	}

	public void passwordGeneratorTest(final String TEST_NAME, String policyFilename) throws JAXBException, SchemaException, FileNotFoundException {
		LOGGER.info("===[ {} ]===", TEST_NAME);
		String pathname = BASE_PATH + policyFilename;
		File file = new File(pathname);
		LOGGER.info("Positive testing {}: {}", TEST_NAME, policyFilename);
		ValuePolicyType pp = PrismTestUtil.unmarshalObject(file, ValuePolicyType.class);
		OperationResult op = new OperationResult(TEST_NAME);
		
		String psswd;
		// generate minimal size passwd
		for (int i = 0; i < 100; i++) {
			psswd = ValuePolicyGenerator.generate(pp.getStringPolicy(), 10, true, op);
			LOGGER.info("Generated password:" + psswd);
			op.computeStatus();
			if (!op.isSuccess()) {
				LOGGER.info("Result:" + op.dump());
				AssertJUnit.fail("Password generator failed:\n"+op.dump());
			}
			assertNotNull(psswd);
			assertPassword(psswd, pp);
		}
		// genereata to meet as possible
		LOGGER.info("-------------------------");
		// Generate up to possible
		for (int i = 0; i < 100; i++) {
			psswd = ValuePolicyGenerator.generate(pp.getStringPolicy(), 10, false, op);
			LOGGER.info("Generated password:" + psswd);
			op.computeStatus();
			if (!op.isSuccess()) {
				LOGGER.info("Result:" + op.dump());
			}
			AssertJUnit.assertTrue(op.isSuccess());
			assertNotNull(psswd);

		}
	}

	private void assertPassword(String passwd, ValuePolicyType pp) {
		OperationResult validationResult = PasswordPolicyUtils.validatePassword(passwd, pp);
		if (!validationResult.isSuccess()) {
			AssertJUnit.fail(validationResult.dump());
		}
	}

	@Test
	public void passwordValidationTest() throws JAXBException, SchemaException, FileNotFoundException {
		String filename = "password-policy-complex.xml";
		String pathname = BASE_PATH + filename;
		File file = new File(pathname);

		LOGGER.error("Positive testing: passwordGeneratorComplexTest");
		ValuePolicyType pp = PrismTestUtil.unmarshalObject(file, ValuePolicyType.class);

		// Test on all cases
		AssertJUnit.assertTrue(pwdValidHelper("582a**A", pp));
		AssertJUnit.assertFalse(pwdValidHelper("58", pp));
		AssertJUnit.assertFalse(pwdValidHelper("333a**aGaa", pp));
		AssertJUnit.assertFalse(pwdValidHelper("AAA4444", pp));
	}

	private boolean pwdValidHelper(String password, ValuePolicyType pp) {
		OperationResult op = new OperationResult("Password Validator test with password:" + password);
		PasswordPolicyUtils.validatePassword(password, pp, op);
		op.computeStatus();
		LOGGER.error(op.dump());
		return (op.isSuccess());
	}

	@Test
	public void passwordValidationMultipleTest() throws JAXBException, SchemaException, FileNotFoundException {
		String filename = "password-policy-complex.xml";
		String pathname = BASE_PATH + filename;
		File file = new File(pathname);
		
		LOGGER.error("Positive testing: passwordGeneratorComplexTest");
		ValuePolicyType pp = PrismTestUtil.unmarshalObject(file, ValuePolicyType.class); 

		String password = "582a**A";
		
		OperationResult op = new OperationResult("Password Validator with multiple policies");
		List<ValuePolicyType> pps = new ArrayList<ValuePolicyType>();
		pps.add(pp);
		pps.add(pp);
		pps.add(pp);
		
		PasswordPolicyUtils.validatePassword(password, pps, op);
		op.computeStatus();
		LOGGER.error(op.dump());
		AssertJUnit.assertTrue(op.isSuccess());
		
	}

	@Test
	public void XMLPasswordPolicy() throws JAXBException, SchemaException, FileNotFoundException {

		String filename = "password-policy-complex.xml";
		String pathname = BASE_PATH + filename;
		File file = new File(pathname);

		ValuePolicyType pp = PrismTestUtil.unmarshalObject(file, ValuePolicyType.class); 

		OperationResult op = new OperationResult("Generator testing");

		// String pswd = PasswordPolicyUtils.generatePassword(pp, op);
		// LOGGER.info("Generated password: " + pswd);
		// assertNotNull(pswd);
		// assertTrue(op.isSuccess());
	}
}