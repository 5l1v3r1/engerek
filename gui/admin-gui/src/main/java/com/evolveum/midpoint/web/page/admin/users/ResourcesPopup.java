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

package com.evolveum.midpoint.web.page.admin.users;

import com.evolveum.midpoint.web.component.button.AjaxLinkButton;
import com.evolveum.midpoint.web.component.data.TablePanel;
import com.evolveum.midpoint.web.component.data.column.CheckBoxHeaderColumn;
import com.evolveum.midpoint.web.component.util.SelectableBean;
import com.evolveum.midpoint.web.page.admin.resources.dto.ResourceDto;
import com.evolveum.midpoint.web.page.admin.users.dto.SimpleUserResourceProvider;
import com.evolveum.midpoint.xml.ns._public.common.common_2a.ResourceType;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.StringResourceModel;

import java.util.ArrayList;
import java.util.List;

public class ResourcesPopup extends Panel {

    public ResourcesPopup(String id, SimpleUserResourceProvider provider) {
        super(id);

        initLayout(provider);
    }

    private void initLayout(SimpleUserResourceProvider provider) {
        TablePanel resources = new TablePanel<ResourceDto>("table",
                provider, initResourceColumns());
        resources.setOutputMarkupId(true);
        add(resources);

        AjaxLinkButton addButton = new AjaxLinkButton("add",
                new StringResourceModel("resourcePopup.button.add", this, null)) {

            @Override
            public void onClick(AjaxRequestTarget target) {
                addPerformed(target, getSelectedResources());
            }
        };
        add(addButton);
    }

    private List<IColumn<ResourceDto, String>> initResourceColumns() {
        List<IColumn<ResourceDto, String>> columns = new ArrayList<IColumn<ResourceDto, String>>();

        IColumn column = new CheckBoxHeaderColumn<ResourceDto>();
        columns.add(column);

        columns.add(new PropertyColumn(new StringResourceModel("resourcePopup.name", this, null), "value.name"));

        return columns;
    }

    private List<ResourceType> getSelectedResources() {
        List<ResourceType> list = new ArrayList<ResourceType>();

        TablePanel table = (TablePanel) get("table");
        SimpleUserResourceProvider provider = (SimpleUserResourceProvider) table.getDataTable().getDataProvider();
        for (SelectableBean<ResourceType> bean : provider.getAvailableData()) {
            if (!bean.isSelected()) {
                continue;
            }

            list.add(bean.getValue());
        }

        return list;
    }

    protected void addPerformed(AjaxRequestTarget target, List<ResourceType> newResources) {

    }
}
