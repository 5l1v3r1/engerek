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
package com.evolveum.midpoint.common.expression.script;

import com.evolveum.midpoint.common.expression.ObjectDeltaObject;
import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.schema.util.ObjectResolver;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ObjectReferenceType;

import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author Radovan Semancik
 */
public class ScriptVariables {

    private Map<QName, Object> variables = new HashMap<QName, Object>();

    private static final Trace LOGGER = TraceManager.getTrace(ScriptVariables.class);

    /**
     * Adds map of extra variables to the expression.
     * If there are variables with deltas (ObjectDeltaObject) the operation fail because
     * it cannot decide which version to use.
     */
    public void addVariableDefinitions(Map<QName, Object> extraVariables) {
        for (Entry<QName, Object> entry : extraVariables.entrySet()) {
        	Object value = entry.getValue();
        	if (value instanceof ObjectDeltaObject<?>) {
        		ObjectDeltaObject<?> odo = (ObjectDeltaObject<?>)value;
        		if (odo.getObjectDelta() != null) {
        			throw new IllegalArgumentException("Cannot use variables with deltas in addVariableDefinitions, use addVariableDefinitionsOld or addVariableDefinitionsNew");
        		}
        		value = odo.getOldObject();
        	}
            variables.put(entry.getKey(), value);
        }
    }

    /**
     * Adds map of extra variables to the expression.
     * If there are variables with deltas (ObjectDeltaObject) it takes the "old" version
     * of the object.
     */
    public void addVariableDefinitionsOld(Map<QName, Object> extraVariables) {
        for (Entry<QName, Object> entry : extraVariables.entrySet()) {
        	Object value = entry.getValue();
        	if (value instanceof ObjectDeltaObject<?>) {
        		ObjectDeltaObject<?> odo = (ObjectDeltaObject<?>)value;
        		value = odo.getOldObject();
        	}
            variables.put(entry.getKey(), value);
        }
    }

    /**
     * Adds map of extra variables to the expression.
     * If there are variables with deltas (ObjectDeltaObject) it takes the "new" version
     * of the object.
     */
    public void addVariableDefinitionsNew(Map<QName, Object> extraVariables) {
        for (Entry<QName, Object> entry : extraVariables.entrySet()) {
        	Object value = entry.getValue();
        	if (value instanceof ObjectDeltaObject<?>) {
        		ObjectDeltaObject<?> odo = (ObjectDeltaObject<?>)value;
        		value = odo.getNewObject();
        	}
            variables.put(entry.getKey(), value);
        }
    }
    
    public void setRootNode(ObjectReferenceType objectRef) {
        addVariableDefinition(null, (Object) objectRef);
    }

    public void addVariableDefinition(QName name, Object value) {
        if (variables.containsKey(name)) {
            LOGGER.warn("Duplicate definition of variable {}", name);
            return;
        }
        variables.put(name, value);
    }
    
    public boolean hasVariableDefinition(QName name) {
    	return variables.containsKey(name);
    }
    
    public Object get(QName name) {
    	return variables.get(name);
    }
    
    public Set<Entry<QName,Object>> entrySet() {
    	return variables.entrySet();
    }

    public String formatVariables() {
        StringBuilder sb = new StringBuilder();
        Iterator<Entry<QName, Object>> i = variables.entrySet().iterator();
        while (i.hasNext()) {
            Entry<QName, Object> entry = i.next();
            SchemaDebugUtil.indentDebugDump(sb, 1);
            sb.append(SchemaDebugUtil.prettyPrint(entry.getKey())).append(": ");
            Object value = entry.getValue();
            if (value instanceof DebugDumpable) {
            	sb.append("\n");
            	sb.append(((DebugDumpable)value).debugDump(2));
            } else if (value instanceof Element) {
            	sb.append("\n");
            	sb.append(DOMUtil.serializeDOMToString(((Element)value)));
            } else {
            	sb.append(SchemaDebugUtil.prettyPrint(value));
            }
            if (i.hasNext()) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Expects QName-value pairs.
     * 
     * E.g.
     * create(var1qname, var1value, var2qname, var2value, ...)
     * 
     * Mostly for testing. Use at your own risk.
     */
    public static ScriptVariables create(Object... parameters) {
    	ScriptVariables vars = new ScriptVariables();
    	for (int i = 0; i < parameters.length; i += 2) {
    		vars.addVariableDefinition((QName)parameters[i], parameters[i+1]);
    	}
    	return vars;
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((variables == null) ? 0 : variables.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ScriptVariables other = (ScriptVariables) obj;
		if (variables == null) {
			if (other.variables != null)
				return false;
		} else if (!variables.equals(other.variables))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ScriptVariables(" + variables + ")";
	}
    
}
