package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.regex.Pattern;

import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.UrlType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.service.TaskHelper;
import dev.dsf.bpe.v2.variables.Variables;

public class PrepareExecution implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(PrepareExecution.class);

	private static final String ISO_8601_DURATION_STRING = "^P(?:([0-9]+)Y)?(?:([0-9]+)M)?(?:([0-9]+)D)?(T(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9]+)(?:[.,]([0-9]{0,9}))?S)?)?$";
	private static final Pattern ISO_8601_DURATION = Pattern.compile(ISO_8601_DURATION_STRING);

	private final String statusTimerInterval;

	public PrepareExecution(String statusTimerInterval)
	{
		this.statusTimerInterval = statusTimerInterval;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		if (!ISO_8601_DURATION.matcher(statusTimerInterval).matches())
			throw new IllegalArgumentException(
					"statusTimerInterval '" + statusTimerInterval + "' not in ISO 8601 time duration format");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_STATUS_TIMER_INTERVAL, statusTimerInterval);

		Task task = variables.getStartTask();

		String projectIdentifier = getProjectIdentifier(api.getTaskHelper(), task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER, projectIdentifier);

		String dmsIdentifier = getDmsIdentifier(api.getTaskHelper(), task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER, dmsIdentifier);

		String contractUrl = getContractUrl(api.getTaskHelper(), task);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_CONTRACT_URL, contractUrl);

		logger.info(
				"Starting execute of approved data-sharing project '{}' to DMS '{}' with contract-url '{}' and status timer interval '{}' in Task '{}'",
				projectIdentifier, dmsIdentifier, contractUrl, statusTimerInterval,
				api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task));
	}

	private String getProjectIdentifier(TaskHelper helper, Task task)
	{
		return helper
				.getFirstInputParameterValue(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_PROJECT_IDENTIFIER, Identifier.class)
				.map(Identifier::getValue).map(String::trim)
				.orElseThrow(() -> new RuntimeException("Task.input:project-identifier missing"));
	}

	private String getDmsIdentifier(TaskHelper helper, Task task)
	{
		return helper
				.getInputParameterValues(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DMS_IDENTIFIER, Reference.class)
				.filter(Reference::hasIdentifier).map(Reference::getIdentifier).map(Identifier::getValue).findFirst()
				.orElseThrow(() -> new RuntimeException("Task.input:dms-identifier missing"));
	}

	private String getContractUrl(TaskHelper helper, Task task)
	{
		return helper
				.getFirstInputParameterValue(task, ConstantsDataSharing.CODESYSTEM_DATA_SHARING,
						ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_CONTRACT_URL, UrlType.class)
				.map(UrlType::getValue).orElseThrow(() -> new RuntimeException("Task.input:contract-url missing"));
	}
}
