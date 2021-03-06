/*
 * Copyright (c) 2010-2013 Evolveum
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
package com.evolveum.midpoint.repo.api;

import java.io.Serializable;

/**
 * @author mederly
 *
 */
public class RepoModifyOptions implements Serializable {
	private static final long serialVersionUID = 478427843213482L;

	// execute MODIFY operation even if the list of changes is empty
	private boolean executeIfNoChanges = false;

	public boolean isExecuteIfNoChanges() {
		return executeIfNoChanges;
	}

	public void setExecuteIfNoChanges(boolean executeIfNoChanges) {
		this.executeIfNoChanges = executeIfNoChanges;
	}

	public static boolean isExecuteIfNoChanges(RepoModifyOptions options) {
		return options != null ? options.isExecuteIfNoChanges() : false;
	}

	public static RepoModifyOptions createExecuteIfNoChanges() {
		RepoModifyOptions opts = new RepoModifyOptions();
		opts.setExecuteIfNoChanges(true);
		return opts;
	}
	
	@Override
	public String toString() {
		return "RepoModifyOptions(executeIfNoChanges=" + executeIfNoChanges + ")";
	}

}
