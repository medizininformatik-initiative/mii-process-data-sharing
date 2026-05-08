package de.medizininformatik_initiative.process.data_sharing.service.merge;

import org.hl7.fhir.r4.model.Task;

import de.medizininformatik_initiative.process.data_sharing.ConstantsDataSharing;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.activity.ServiceTask;
import dev.dsf.bpe.v2.client.dsf.DelayStrategy;
import dev.dsf.bpe.v2.client.dsf.DsfClient;
import dev.dsf.bpe.v2.variables.Variables;

public class HandleErrorMergeReceiveSendReceipt implements ServiceTask
{
	private final boolean dmsEmailEnabled;

	public HandleErrorMergeReceiveSendReceipt(boolean dmsEmailEnabled)
	{
		this.dmsEmailEnabled = dmsEmailEnabled;
	}

	@Override
	public void execute(ProcessPluginApi api, Variables variables)
	{
		Task startTask = variables.getStartTask();
		Task latestTask = variables.getLatestTask();

		if (dmsEmailEnabled)
			sendMail(api, variables, startTask);

		failTaskIfNotStartTask(api.getDsfClientProvider().getLocal(), startTask, latestTask, variables);
	}

	private void sendMail(ProcessPluginApi api, Variables variables, Task task)
	{
		String error = variables
				.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_DATA_SHARING_MERGE_RECEIVE_ERROR_MESSAGE);
		String projectIdentifier = variables.getString(ConstantsDataSharing.BPMN_EXECUTION_VARIABLE_PROJECT_IDENTIFIER);

		String subject = "Error in process '" + ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "'";
		String message = "Could not send receipt after successful download, decrypt, validate and insert data-set in process  '"
				+ ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING + "' and Task '"
				+ api.getTaskHelper().getLocalVersionlessAbsoluteUrl(task) + "' from organization '"
				+ task.getRequester().getIdentifier().getValue() + "' and project-identifier '" + projectIdentifier
				+ "':\n" + "- status code: " + ConstantsBase.CODESYSTEM_DATA_SET_STATUS_VALUE_RECEIVE_ERROR + "\n"
				+ "- error: " + (error == null ? "none" : error);

		api.getMailService().send(subject, message);
	}

	private void failTaskIfNotStartTask(DsfClient client, Task startTask, Task latestTask, Variables variables)
	{
		if (latestTask != null && Task.TaskStatus.FAILED != latestTask.getStatus() && startTask != latestTask)
		{
			latestTask.setStatus(Task.TaskStatus.FAILED);
			client.withRetry(ConstantsBase.DSF_CLIENT_RETRY_6_TIMES,
					DelayStrategy.constant(ConstantsBase.DSF_CLIENT_RETRY_INTERVAL_5MIN)).update(latestTask);
			variables.updateTask(latestTask);
		}
	}
}
