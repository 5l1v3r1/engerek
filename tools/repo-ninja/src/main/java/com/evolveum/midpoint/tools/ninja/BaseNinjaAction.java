/*
 * Copyright (c) 2012 Evolveum
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
 * Portions Copyrighted 2013 [name of copyright owner]
 */

package com.evolveum.midpoint.tools.ninja;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author lazyman
 */
public abstract class BaseNinjaAction {

    public static final String[] CONTEXTS = {"classpath:ctx-ninja.xml", "classpath:ctx-common.xml",
            "classpath:ctx-configuration.xml", "classpath*:ctx-repository.xml", "classpath:ctx-repo-cache.xml",
            "classpath:ctx-audit.xml"};

    protected void destroyContext(ClassPathXmlApplicationContext context) {
        if (context != null) {
            try {
                context.destroy();
            } catch (Exception ex) {
                System.out.println("Exception occurred during context shutdown, reason: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }
}
