```java
package com.rbs.tntr.business.taggingService.transformer;

import com.nwm.tntr.commons.domain.JiraReference;
import com.nwm.tntr.commons.enums.ApiQueryFilter;
import com.nwm.tntr.commons.enums.TntrCallerService;
import com.nwm.tntr.commons.enums.TntrFieldsReadUpdateEnum;
import com.nwm.tntr.commons.repository.domain.ReportingTemplate;
import com.nwm.tntr.commons.repository.domain.RepositoryAction;
import com.nwm.tntr.commons.repository.domain.TntrRepositoryReadCommandContext;
import com.nwm.tntr.commons.repository.domain.TntrRepositoryReadCommandResult;
import com.nwm.tntr.commons.repository.domain.TntrRepositoryUpdateCommandContext;
import com.nwm.tntr.commons.repository.domain.TntrRepositoryUpdateCommandResult;
import com.nwm.tntr.commons.repository.util.TntrTdxNodeBuilderUtil;
import com.rbs.tntr.business.taggingService.service.common.DateTimeService;
import com.rbs.tntr.domain.taggingService.jira