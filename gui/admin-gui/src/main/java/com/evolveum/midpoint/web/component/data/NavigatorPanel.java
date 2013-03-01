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

package com.evolveum.midpoint.web.component.data;

import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.navigation.paging.AjaxPagingNavigator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.AbstractLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.navigation.paging.IPageable;
import org.apache.wicket.markup.html.navigation.paging.IPagingLabelProvider;
import org.apache.wicket.markup.html.navigation.paging.PagingNavigation;
import org.apache.wicket.markup.repeater.data.DataViewBase;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.StringResourceModel;

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.util.LoadableModel;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;

/**
 * @author lazyman
 */
public class NavigatorPanel extends AjaxPagingNavigator {

    private static final Trace LOGGER = TraceManager.getTrace(NavigatorPanel.class);
    private boolean showPageListing = true;

    public NavigatorPanel(String id, IPageable pageable, boolean showPageListing) {
        super(id, pageable);
        this.showPageListing = showPageListing;

        Label label = new Label("label", createModel(pageable));
        label.add(createVisibilityForSimplePaging());
        add(label);
    }

    private VisibleEnableBehaviour createVisibleBehaviour(final IPageable pageable) {
        return new VisibleEnableBehaviour() {

            @Override
            public boolean isVisible() {
                return pageable.getPageCount() != 0;
            }
        };
    }

    @Override
    protected PagingNavigation newNavigation(String id, IPageable pageable, IPagingLabelProvider labelProvider) {
        PagingNavigation navigation = super.newNavigation(id, pageable, labelProvider);
        navigation.setOutputMarkupId(true);
        navigation.add(createVisibilityForSimplePaging());
        return navigation;
    }

    private VisibleEnableBehaviour createVisibilityForSimplePaging() {
        return new VisibleEnableBehaviour() {

            @Override
            public boolean isVisible() {
                return showPageListing;
            }
        };
    }

    @Override
    protected AbstractLink newPagingNavigationIncrementLink(String id, IPageable pageable, int increment) {
        AbstractLink link = super.newPagingNavigationIncrementLink(id, pageable, increment);
        link.setOutputMarkupId(true);
        link.add(createVisibleBehaviour(pageable));
        return link;
    }

	@Override
	protected AbstractLink newPagingNavigationLink(String id, IPageable pageable, int pageNumber) {
        AbstractLink  link = super.newPagingNavigationLink(id, pageable, pageNumber);
		link.setOutputMarkupId(true);
		link.add(createVisibleBehaviour(pageable));
    	return link;
	}
	
	@Override
	protected void onAjaxEvent(AjaxRequestTarget target) {
		super.onAjaxEvent(target);
		target.appendJavaScript("initTable();");
	}

    private IModel<String> createModel(final IPageable pageable) {
        return new LoadableModel<String>() {

            @Override
            protected String load() {
                long from = 0;
                long to = 0;
                long count = 0;

                if (pageable instanceof DataViewBase) {
                    DataViewBase view = (DataViewBase) pageable;

                    from = view.getFirstItemOffset() + 1;
                    to = from + view.getItemsPerPage() - 1;
                    long itemCount = view.getItemCount();
                    if (to > itemCount) {
                        to = itemCount;
                    }
                    count = itemCount;
                } else if (pageable instanceof DataTable) {
                    DataTable table = (DataTable) pageable;

                    from = table.getCurrentPage() * table.getItemsPerPage() + 1;
                    to = from + table.getItemsPerPage() - 1;
                    long itemCount = table.getItemCount();
                    if (to > itemCount) {
                        to = itemCount;
                    }
                    count = itemCount;
                } else {
                    LOGGER.warn("Navigator panel, missing implementation... TODO");
                }

                if (count > 0) {
                    return new StringResourceModel("navigatorPanel.label", NavigatorPanel.this, null,
                            new Object[]{from, to, count}).getString();
                }

                return new StringResourceModel("navigatorPanel.noFound", NavigatorPanel.this, null).getString();
            }
        };
    }
}
