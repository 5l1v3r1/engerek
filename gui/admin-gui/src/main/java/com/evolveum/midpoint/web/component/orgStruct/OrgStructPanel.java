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

package com.evolveum.midpoint.web.component.orgStruct;

import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.resource.PackageResourceReference;

import com.evolveum.midpoint.web.page.admin.users.dto.OrgStructDto;

/**
 * @author mserbak
 */
public class OrgStructPanel extends OptionalTree {
	private NestedTree<NodeDto> tree;

	public OrgStructPanel(String id, IModel<OrgStructDto> model) {
		super(id, model);
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);

        response.render(CssHeaderItem.forReference(
                new PackageResourceReference(OrgStructPanel.class, "OrgStructPanel.css")));
        response.render(JavaScriptHeaderItem.forReference(
                new PackageResourceReference(OrgStructPanel.class, "OrgStructPanel.js")));
        response.render(OnDomReadyHeaderItem.forScript("initOrgStruct()"));
    }

	protected AbstractTree<NodeDto> createTree(OrgStructProvider provider, IModel<Set<NodeDto>> state) {
		tree = new NestedTree<NodeDto>("tabletree", provider, state) {

			@Override
			protected Component newContentComponent(String id, IModel<NodeDto> model) {
				return OrgStructPanel.this.newContentComponent(id, model);
			}

			@Override
			public Component newNodeComponent(String id, IModel<NodeDto> model) {
				return OrgStructPanel.this.newNodeComponent(id, model);
			}
		};
		return tree;
	}
}
