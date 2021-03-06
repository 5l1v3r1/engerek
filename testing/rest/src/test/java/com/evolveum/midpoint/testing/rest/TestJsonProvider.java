/*
 * Copyright (c) 2013-2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.testing.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.commons.io.IOUtils;

import com.evolveum.midpoint.model.impl.rest.MidpointAbstractProvider;
import com.evolveum.midpoint.model.impl.rest.MidpointJsonProvider;
import com.evolveum.midpoint.model.impl.rest.MidpointXmlProvider;
import com.evolveum.midpoint.prism.PrismObject;

public class TestJsonProvider<T> extends MidpointJsonProvider<T> {
	
	
	@Override
	public T readFrom(Class<T> type, Type genericType, Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
					throws IOException, WebApplicationException {
		
		if (String.class.isAssignableFrom(type.getClass())) {
			return (T) IOUtils.toString(entityStream);
		}
		
		T result = super.readFrom(type, genericType, annotations, mediaType, httpHeaders, entityStream);
		if (result instanceof PrismObject) {
			return (T) ((PrismObject) result).asObjectable();
		}
		
		return result;
	}
	
}