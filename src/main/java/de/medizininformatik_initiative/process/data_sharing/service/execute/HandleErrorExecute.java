package de.medizininformatik_initiative.process.data_sharing.service.execute;

import java.util.Objects;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;

import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.variables.Variables;

public class HandleErrorExecute implements ServiceTask, InitializingBean
{
	private static final Logger logger = LoggerFactory.getLogger(HandleErrorExecute.class);

	private final DataSetStatusGenerator statusGenerator;
	private final boolean dicEmailEnabled;

	public HandleErrorExecute(DataSetStatusGenerator statusGenerator, boolean dicEmailEnabled)
	{
		this.statusGenerator = statusGenerator;
		this.dicEmailEnabled = dicEmailEnabled;
	}

	@Override
	public void afterPropertiesSet()
	{
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();
		String errorCode = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR);
		String errorMessage = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE);

		failTaskIfNotStartTask(api, startTask, latestTask, errorCode, errorMessage, variables);

		logger.warn("Recreating user-task 'release-data-set'");
		if (dicEmailEnabled)
			sendMail(api, startTask, variables, errorMessage);

		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR, null);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_EXECUTE_ERROR_MESSAGE, null);
	}

	private void sendMail(ProcessPluginApi api, Task task, Variables variables, String errorMessage)
	{
		String dmsIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DMS_IDENTIFIER);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);

		String statusCode = task.getOutput().stream().filter(o -> o.getValue() instanceof Coding)
				.map(o -> (Coding) o.getValue())
				.filter(c -> ConstantsBase.CODESYSTEM_DATA_SET_STATUS.equals(c.getSystem())).map(Coding::getCode)
				.findFirst().orElse("unknown");

		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "'";
		String message = "Could not provide data-set in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING + "' and Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' for DMS '" + dmsIdentifier
				+ "' regarding project-identifier '" + projectIdentifier + "':\n" + "- status code: " + statusCode
				+ "\n" + "- error: " + (errorMessage == null ? "none" : errorMessage);

		api.getMailService().send(subject, message);
	}

	private void failTaskIfNotStartTask(ProcessPluginApi api, Task startTask, Task latestTask, String errorCode,
			String errorMessage, Variables variables)
	{
		if (latestTask != null && startTask != latestTask)
		{
			latestTask.setStatus(Task.TaskStatus.FAILED);
			latestTask.addOutput(statusGenerator.createDataSetStatusOutput(
					api.getProcessPluginDefinition().getResourceVersion(), errorCode,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING, api.getProcessPluginDefinition().getResourceVersion(),
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS, errorMessage));
			variables.updateTask(latestTask);

			api.getDsfClientProvider().getLocal().withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
					DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)).update(latestTask);
		}
	}
}
