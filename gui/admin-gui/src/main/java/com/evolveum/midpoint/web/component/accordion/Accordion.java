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

package com.evolveum.midpoint.web.component.accordion;

import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.border.Border;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.PackageResourceReference;

public class Accordion extends Border {

    private static final long serialVersionUID = 7554515215048790384L;
    private boolean expanded = false;
    private boolean multipleSelect = true;
    private int openedPanel = -1;

    public Accordion(String id) {
        super(id);

        add(new AttributeAppender("class", new Model<String>("accordions"), " "));

        WebMarkupContainer parent = new WebMarkupContainer("parent");
        parent.setOutputMarkupId(true);
        addToBorder(parent);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);

        response.render(JavaScriptHeaderItem.forReference(
                new PackageResourceReference(Accordion.class, "Accordion.js")));
        response.render(CssHeaderItem.forReference(
                new PackageResourceReference(Accordion.class, "Accordion.css")));

        WebMarkupContainer parent = (WebMarkupContainer) get("parent");
        response.render(OnDomReadyHeaderItem.forScript("createAccordion('" + parent.getMarkupId()
                + "'," + getExpanded() + "," + getMultipleSelect() + "," + getOpenedPanel() + ")"));
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void setMultipleSelect(boolean multipleSelect) {
        this.multipleSelect = multipleSelect;
    }

    public void setOpenedPanel(int openedPanel) {
        this.openedPanel = openedPanel;
    }

    public boolean getExpanded() {
        return expanded;
    }

    private int getMultipleSelect() {
        if (multipleSelect) {
            return 0;
        }
        return -1;
    }

    public int getOpenedPanel() {
        return openedPanel;
    }
}
