/**
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
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.common.expression;

import java.util.ArrayList;
import java.util.Collection;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.prism.Item;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PartiallyResolvedValue;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.PrismProperty;
import com.evolveum.midpoint.prism.PrismPropertyDefinition;
import com.evolveum.midpoint.prism.PrismPropertyValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.Structured;
import com.evolveum.midpoint.prism.delta.ContainerDelta;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PrismValueDeltaSetTriple;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.util.exception.SchemaException;

/**
 * @author semancik
 *
 */
public class ItemDeltaItem<V extends PrismValue> {

	Item<V> itemOld;
	ItemDelta<V> delta;
	Item<V> itemNew;
	ItemPath residualPath = null;
	
	public ItemDeltaItem() {
		
	}
	
	public ItemDeltaItem(Item<V> itemOld, ItemDelta<V> delta, Item<V> itemNew) {
		super();
		this.itemOld = itemOld;
		this.delta = delta;
		this.itemNew = itemNew;
	}

	public ItemDeltaItem(ItemDeltaItem<V> idi) {
		super();
		this.itemOld = idi.getItemOld();
		this.itemNew = idi.getItemNew();
		this.delta = idi.getDelta();
	}

	public Item<V> getItemOld() {
		return itemOld;
	}

	public void setItemOld(Item<V> itemOld) {
		this.itemOld = itemOld;
	}
	
	public ItemDelta<V> getDelta() {
		return delta;
	}

	public void setDelta(ItemDelta<V> delta) {
		this.delta = delta;
	}

	public Item<V> getItemNew() {
		return itemNew;
	}

	public void setItemNew(Item<V> itemNew) {
		this.itemNew = itemNew;
	}
	
	public Item<V> getAnyItem() {
		if (itemOld != null) {
			return itemOld;
		}
		return itemNew;
	}
	
	public ItemPath getResidualPath() {
		return residualPath;
	}

	public void setResidualPath(ItemPath residualPath) {
		this.residualPath = residualPath;
	}

	public boolean isNull() {
		return itemOld == null && itemNew == null && delta == null;
	}
	
	public QName getElementName() {
		Item<V> anyItem = getAnyItem();
		if (anyItem != null) {
			return anyItem.getName();
		}
		if (delta != null) {
			return delta.getName();
		}
		return null;
	}
	
	public ItemDefinition getDefinition() {
		Item<V> anyItem = getAnyItem();
		if (anyItem != null) {
			return anyItem.getDefinition();
		}
		if (delta != null) {
			return delta.getDefinition();
		}
		return null;
	}
	
	public void recompute() throws SchemaException {
		if (delta != null) {
			itemNew = delta.getItemNew(itemOld);
		} else {
			itemNew = itemOld;
		}
	}

	public <X extends PrismValue> ItemDeltaItem<X> findIdi(ItemPath path) {
		if (path.isEmpty()) {
			return (ItemDeltaItem<X>) this;
		}
		Item<X> subItemOld = null;
		ItemPath subResidualPath = null;
		if (itemOld != null) {
			PartiallyResolvedValue<X> partialItemOld = itemOld.findPartial(path);
			if (partialItemOld != null) {
				subItemOld = partialItemOld.getItem();
				subResidualPath = partialItemOld.getResidualPath();
			}
		}
		Item<X> subItemNew = null;
		if (itemNew != null) {
			PartiallyResolvedValue<X> partialItemNew = itemNew.findPartial(path);
			if (partialItemNew != null) {
				subItemNew = partialItemNew.getItem();
				if (subResidualPath == null) {
					subResidualPath = partialItemNew.getResidualPath();
				}
			}
		}
		ItemDelta<X> subItemDelta= null;
		if (delta != null) {
			if (delta instanceof ContainerDelta<?>) {
				subItemDelta = (ItemDelta<X>) ((ContainerDelta<?>)delta).findItemDelta(path);
			}
		}
		ItemDeltaItem<X> subIdi = new ItemDeltaItem<X>(subItemOld, subItemDelta, subItemNew);
		subIdi.setResidualPath(subResidualPath);
		return subIdi;
	}
	
	public PrismValueDeltaSetTriple<V> toDeltaSetTriple() {
		return ItemDelta.toDeltaSetTriple(itemOld, delta);
	}

	public boolean isContainer() {
		Item<V> item = getAnyItem();
		if (item != null) {
			return item instanceof PrismContainer<?>;
		}
		if (getDelta() != null) {
			return getDelta() instanceof ContainerDelta<?>;
		}
		return false;
	}
	
	public boolean isProperty() {
		Item<V> item = getAnyItem();
		if (item != null) {
			return item instanceof PrismProperty<?>;
		}
		if (getDelta() != null) {
			return getDelta() instanceof PropertyDelta<?>;
		}
		return false;
	}

	/**
	 * @return
	 */
	public boolean isStructuredProperty() {
		if (!isProperty()) {
			return false;
		}
		PrismProperty<?> property = (PrismProperty<?>) getAnyItem();
		Object realValue = property.getAnyRealValue();
		if (realValue != null) {
			return realValue instanceof Structured;
		}
		PropertyDelta<?> delta = (PropertyDelta<?>) getDelta();
		realValue = delta.getAnyRealValue();
		if (realValue != null) {
			return realValue instanceof Structured;
		}
		return false;
	}

	// Assumes that this IDI represents structured property
	public <X> ItemDeltaItem<PrismPropertyValue<X>> resolveStructuredProperty(ItemPath resolvePath, PrismPropertyDefinition outputDefinition, ItemPath outputPath) {
		ItemDeltaItem<PrismPropertyValue<Structured>> thisIdi = (ItemDeltaItem<PrismPropertyValue<Structured>>)this;
		PrismProperty<X> outputPropertyNew = resolveStructuredPropertyItem((PrismProperty<Structured>) thisIdi.getItemNew(), resolvePath, outputDefinition);
		PrismProperty<X> outputPropertyOld = resolveStructuredPropertyItem((PrismProperty<Structured>) thisIdi.getItemOld(), resolvePath, outputDefinition);
		PropertyDelta<X> outputDelta = resolveStructuredPropertyDelta((PropertyDelta<Structured>) thisIdi.getDelta(), resolvePath, outputDefinition, outputPath);
		return new ItemDeltaItem<PrismPropertyValue<X>>(outputPropertyOld, outputDelta, outputPropertyNew);
	}

	private <X> PrismProperty<X> resolveStructuredPropertyItem(PrismProperty<Structured> sourceProperty, ItemPath resolvePath, PrismPropertyDefinition outputDefinition) {
		if (sourceProperty == null) {
			return null;
		}
		PrismProperty<X> outputProperty = outputDefinition.instantiate();
		for (Structured sourceRealValue: sourceProperty.getRealValues()) {
			X outputRealValue = (X) sourceRealValue.resolve(resolvePath);
			outputProperty.addRealValue(outputRealValue);
		}
		return outputProperty;
	}
	
	private <X> PropertyDelta<X> resolveStructuredPropertyDelta(PropertyDelta<Structured> sourceDelta, ItemPath resolvePath, PrismPropertyDefinition outputDefinition, ItemPath outputPath) {
		if (sourceDelta == null) {
			return null;
		}
		PropertyDelta<X> outputDelta = (PropertyDelta<X>) outputDefinition.createEmptyDelta(outputPath);
		Collection<PrismPropertyValue<X>> outputValuesToAdd = resolveStructuredDeltaSet(sourceDelta.getValuesToAdd(), resolvePath);
		if (outputValuesToAdd != null) {
			outputDelta.addValuesToAdd(outputValuesToAdd);
		}
		Collection<PrismPropertyValue<X>> outputValuesToDelete = resolveStructuredDeltaSet(sourceDelta.getValuesToDelete(), resolvePath);
		if (outputValuesToDelete != null) {
			outputDelta.addValuesToDelete(outputValuesToDelete);
		}
		Collection<PrismPropertyValue<X>> outputValuesToReplace = resolveStructuredDeltaSet(sourceDelta.getValuesToReplace(), resolvePath);
		if (outputValuesToReplace != null) {
			outputDelta.setValuesToReplace(outputValuesToReplace);
		}
		return outputDelta;
	}
	
	private <X> Collection<PrismPropertyValue<X>> resolveStructuredDeltaSet(Collection<PrismPropertyValue<Structured>> set, ItemPath resolvePath) {
		if (set == null) {
			return null;
		}
		Collection<PrismPropertyValue<X>> outputSet = new ArrayList<PrismPropertyValue<X>>(set.size());
		for (PrismPropertyValue<Structured> structuredPVal: set) {
			Structured structured = structuredPVal.getValue();
			X outputRval = (X) structured.resolve(resolvePath);
			outputSet.add(new PrismPropertyValue<X>(outputRval));
		}
		return outputSet;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((delta == null) ? 0 : delta.hashCode());
		result = prime * result + ((itemNew == null) ? 0 : itemNew.hashCode());
		result = prime * result + ((itemOld == null) ? 0 : itemOld.hashCode());
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
		ItemDeltaItem other = (ItemDeltaItem) obj;
		if (delta == null) {
			if (other.delta != null)
				return false;
		} else if (!delta.equals(other.delta))
			return false;
		if (itemNew == null) {
			if (other.itemNew != null)
				return false;
		} else if (!itemNew.equals(other.itemNew))
			return false;
		if (itemOld == null) {
			if (other.itemOld != null)
				return false;
		} else if (!itemOld.equals(other.itemOld))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "IDI(old=" + itemOld + ", delta=" + delta + ", new=" + itemNew + ")";
	}
	
	
	
}
