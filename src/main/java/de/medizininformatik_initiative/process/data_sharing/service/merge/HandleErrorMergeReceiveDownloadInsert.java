package de.medizininformatik_initiative.process.data_sharing.service.merge;

import java.util.Objects;

import org.hl7.fhir.r4.model.Task;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import de.medizininformatik_initiative.processes.common.util.DataSetStatusGenerator;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.variables.Variables;

public class HandleErrorMergeReceiveDownloadInsert implements ServiceTask, InitializingBean
{
	private final DataSetStatusGenerator statusGenerator;
	private final boolean dmsEmailEnabled;

	public HandleErrorMergeReceiveDownloadInsert(DataSetStatusGenerator statusGenerator, boolean dmsEmailEnabled)
	{
		this.statusGenerator = statusGenerator;
		this.dmsEmailEnabled = dmsEmailEnabled;
	}

	@Override
	public void afterPropertiesSet() throws Exception
	{
		Objects.requireNonNull(statusGenerator, "statusGenerator");
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();
		String errorCode = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR);
		String errorMessage = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RELEASE_ERROR_MESSAGE);

		if (dmsEmailEnabled)
			sendMail(api, variables, startTask, errorMessage);

		failAndAddOutputLatestTaskIfNotStartTask(api, startTask, latestTask, errorCode, errorMessage, variables);

		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR, null);
		variables.setString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_MESSAGE,
				null);
	}

	private void sendMail(ProcessPluginApi api, Variables variables, Task task, String error)
	{
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);

		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "'";
		String message = "Could not download, decrypt, validate or insert data-set in process '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' and Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' from organization '"
				+ task.getRequester().getIdentifier().getValue() + "' and project-identifier '" + projectIdentifier
				+ "':\n" + "- status code: " + ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_ERROR + "\n"
				+ "- error: " + (error == null ? "none" : error);

		api.getMailService().send(subject, message);
	}

	private void failAndAddOutputLatestTaskIfNotStartTask(ProcessPluginApi api, Task startTask, Task latestTask,
			String errorCode, String errorMessage, Variables variables)
	{
		if (latestTask != null && startTask != latestTask)
		{
			latestTask.setStatus(Task.TaskStatus.FAILED);
			latestTask.addOutput(statusGenerator.createDataSetStatusOutput(
					api.getProcessPluginDefinition().getResourceVersion(), errorCode,
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING, api.getProcessPluginDefinition().getResourceVersion(),
					ConstantsDataSharing.CODESYSTEM_DATA_SHARING_VALUE_DATA_SET_STATUS, errorMessage));
			variables.updateTask(latestTask);
		}
	}
}
