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
 * Portions Copyrighted 2012 [name of copyright owner]
 */

package com.evolveum.midpoint.web.page.admin.users.dto;

import java.io.Serializable;

import com.evolveum.midpoint.prism.PrismPropertyValue;

/**
 * @author mserbak
 */

public class SubmitPropertiesDto implements Serializable {

	private PrismPropertyValue submitedProperties;
	private SubmitStatus status;

	public SubmitPropertiesDto(PrismPropertyValue submitedProperties, SubmitStatus status) {
		this.submitedProperties = submitedProperties;
		this.status = status;
	}

	public PrismPropertyValue getSubmitedProperties() {
		return submitedProperties;
	}

	public SubmitStatus getStatus() {
		return status;
	}
	
}
