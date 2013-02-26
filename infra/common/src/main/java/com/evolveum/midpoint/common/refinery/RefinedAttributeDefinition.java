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
 */
package com.evolveum.midpoint.common.refinery;

import com.evolveum.midpoint.prism.PrismContext;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.processor.ObjectClassComplexTypeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceAttributeDefinition;
import com.evolveum.midpoint.schema.processor.ResourceAttributeContainerDefinition;
import com.evolveum.midpoint.schema.util.SchemaDebugUtil;
import com.evolveum.midpoint.util.DebugDumpable;
import com.evolveum.midpoint.util.Dumpable;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.AccessType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.MappingType;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceAttributeDefinitionType;

import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.util.List;

/**
 * @author semancik
 */
public class RefinedAttributeDefinition extends ResourceAttributeDefinition implements Dumpable, DebugDumpable {

    private String displayName;
    private String description;
    private boolean tolerant = true;
    private boolean create = true;
    private boolean read = true;
    private boolean update = true;
    private Integer minOccurs = null;
    private Integer maxOccurs = null;
    private ResourceAttributeDefinition attributeDefinition;
    private MappingType outboundMappingType;
    private List<MappingType> inboundMappingTypes;

    private RefinedAttributeDefinition(ResourceAttributeDefinition attrDef, PrismContext prismContext) {
        super(attrDef.getName(), attrDef.getDefaultName(), attrDef.getTypeName(), prismContext);
        this.attributeDefinition = attrDef;
    }

    @Override
    public void setNativeAttributeName(String nativeAttributeName) {
        throw new UnsupportedOperationException("Parts of refined attribute are immutable");
    }

    public boolean isTolerant() {
		return tolerant;
	}

	public void setTolerant(boolean tolerant) {
		this.tolerant = tolerant;
	}

	@Override
    public boolean canRead() {
        return read;
    }

    @Override
    public boolean canUpdate() {
        return update;
    }

    @Override
    public void setReadOnly() {
        throw new UnsupportedOperationException("Parts of refined attribute are immutable");
    }

    @Override
    public QName getValueType() {
        return attributeDefinition.getValueType();
    }

    @Override
    public void setMinOccurs(int minOccurs) {
    	throw new UnsupportedOperationException("Parts of refined attribute are immutable");
    }

    @Override
    public void setMaxOccurs(int maxOccurs) {
    	throw new UnsupportedOperationException("Parts of refined attribute are immutable");
    }

    @Override
    public void setRead(boolean read) {
        throw new UnsupportedOperationException("Parts of refined attribute are immutable");
    }

    @Override
    public void setUpdate(boolean update) {
        throw new UnsupportedOperationException("Parts of refined attribute are immutable");
    }

    @Override
    public void setCreate(boolean create) {
        throw new UnsupportedOperationException("Parts of refined attribute are immutable");
    }

    @Override
    public boolean canCreate() {
        return create;
    }

    @Override
    public QName getDefaultName() {
        return attributeDefinition.getDefaultName();
    }

    @Override
    public boolean isIgnored() {
        return ignored;
    }

    @Override
    public void setIgnored(boolean ignored) {
        throw new UnsupportedOperationException("Parts of refined attribute are immutable");
    }

    @Override
    public void setHelp(String help) {
        throw new UnsupportedOperationException("Parts of refined attribute are immutable");
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ResourceAttributeDefinition getAttributeDefinition() {
        return attributeDefinition;
    }

    public void setAttributeDefinition(ResourceAttributeDefinition attributeDefinition) {
        this.attributeDefinition = attributeDefinition;
    }

    public MappingType getOutboundMappingType() {
        return outboundMappingType;
    }

    public void setOutboundMappingType(MappingType outboundMappingType) {
        this.outboundMappingType = outboundMappingType;
    }
    
    public boolean hasOutboundMapping() {
    	return outboundMappingType != null;
    }

    public List<MappingType> getInboundMappingTypes() {
        return inboundMappingTypes;
    }

    public void setInboundMappingTypes(List<MappingType> inboundAssignmentTypes) {
        this.inboundMappingTypes = inboundAssignmentTypes;
    }

    public QName getName() {
        return attributeDefinition.getName();
    }

    public QName getTypeName() {
        return attributeDefinition.getTypeName();
    }

    public String getNativeAttributeName() {
        return attributeDefinition.getNativeAttributeName();
    }

    public Object[] getAllowedValues() {
        return attributeDefinition.getAllowedValues();
    }

    public int getMaxOccurs() {
    	if (maxOccurs != null) {
    		return maxOccurs;
    	}
        return attributeDefinition.getMaxOccurs();
    }

    public int getMinOccurs() {
    	if (minOccurs != null) {
    		return minOccurs;
    	}
        return attributeDefinition.getMinOccurs();
    }


    public String getHelp() {
        return attributeDefinition.getHelp();
    }

    static RefinedAttributeDefinition parse(ResourceAttributeDefinition schemaAttrDef, ResourceAttributeDefinitionType schemaHandlingAttrDefType,
    		ObjectClassComplexTypeDefinition objectClassDef, PrismContext prismContext, 
                                            String contextDescription) throws SchemaException {

        RefinedAttributeDefinition rAttrDef = new RefinedAttributeDefinition(schemaAttrDef, prismContext);

        if (schemaHandlingAttrDefType != null && schemaHandlingAttrDefType.getDisplayName() != null) {
            rAttrDef.setDisplayName(schemaHandlingAttrDefType.getDisplayName());
        } else {
            if (schemaAttrDef.getDisplayName() != null) {
                rAttrDef.setDisplayName(schemaAttrDef.getDisplayName());
            }
        }

        if (schemaHandlingAttrDefType != null) {
        	
        	if (schemaHandlingAttrDefType.getDescription() != null) {
            	rAttrDef.setDescription(schemaHandlingAttrDefType.getDescription());
        	}

        	if (schemaHandlingAttrDefType.isTolerant() == null) {
        		rAttrDef.tolerant = true;
        	} else {
        		rAttrDef.tolerant = schemaHandlingAttrDefType.isTolerant();
        	}
        	
            if (schemaHandlingAttrDefType.getOutbound() != null) {
                rAttrDef.setOutboundMappingType(schemaHandlingAttrDefType.getOutbound());
            }

            if (schemaHandlingAttrDefType.getInbound() != null) {
                rAttrDef.setInboundMappingTypes(schemaHandlingAttrDefType.getInbound());
            }
        
            rAttrDef.minOccurs = schemaHandlingAttrDefType.getMinOccurs();
            rAttrDef.maxOccurs = schemaHandlingAttrDefType.getMaxOccurs();
            
            if (schemaHandlingAttrDefType.isIgnore() == null) {
            	rAttrDef.ignored = schemaAttrDef.isIgnored();
            } else {
            	rAttrDef.ignored = schemaHandlingAttrDefType.isIgnore();
            }
            
        } else {
        	rAttrDef.ignored = schemaAttrDef.isIgnored();
        }
        
        rAttrDef.create = parseAccess(schemaHandlingAttrDefType, AccessType.CREATE, schemaAttrDef.canCreate());
        rAttrDef.update = parseAccess(schemaHandlingAttrDefType, AccessType.UPDATE, schemaAttrDef.canUpdate());
        rAttrDef.read = parseAccess(schemaHandlingAttrDefType, AccessType.READ, schemaAttrDef.canRead());

        return rAttrDef;

    }

	private static boolean parseAccess(ResourceAttributeDefinitionType attrDefType, AccessType access, boolean defaultValue) {
		if (attrDefType == null) {
			return defaultValue;
		}
		List<AccessType> accessList = attrDefType.getAccess();
		if (accessList == null || accessList.isEmpty()) {
			return defaultValue;
		}
		for (AccessType acccessEntry: accessList) {
			if (acccessEntry == access) {
				return true;
			}
		}
		return false;
	}

	public static boolean isIgnored(ResourceAttributeDefinitionType attrDefType) {
        if (attrDefType.isIgnore() == null) {
            return false;
        }
        return attrDefType.isIgnore();
    }
    
    @Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString());
		if (getDisplayName() != null) {
            sb.append(",Disp");
        }
        if (getDescription() != null) {
            sb.append(",Desc");
        }
        if (getOutboundMappingType() != null) {
            sb.append(",OUT");
        }
        if (getInboundMappingTypes() != null) {
            sb.append(",IN");
        }
		return sb.toString();
	}
    
    /**
     * Return a human readable name of this class suitable for logs.
     */
    @Override
    protected String getDebugDumpClassName() {
        return "RRAD";
    }


}
